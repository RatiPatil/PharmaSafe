import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ReportEvidencePanel } from "./ReportEvidencePanel";
const mocks = vi.hoisted(() => ({ api: vi.fn(), download: vi.fn() }));
vi.mock("../config/api", () => ({
  apiRequest: mocks.api,
  downloadPdf: mocks.download,
}));
const evidence = {
  report: {
    id: 7,
    reportId: "RPT-7",
    reportType: "PSUR",
    product: "Study product",
    authority: "FDA",
    status: "Submitted",
    dueDate: "2026-11-25",
    rowVersion: 3,
    totalCases: 3,
    seriousCases: 2,
    fatalCases: 1,
  },
  documents: [
    {
      id: "doc-1",
      fileName: "report.pdf",
      generatedAt: "2026-11-14T10:00:00Z",
      generatedBy: "reporter",
      sha256: "a".repeat(64),
    },
  ],
  acknowledgements: [],
  documentCount: 1,
  acknowledgementCount: 0,
};
beforeEach(() => {
  vi.clearAllMocks();
  mocks.api.mockResolvedValue(evidence);
  mocks.download.mockResolvedValue(undefined);
});
it("shows real recorded counts and downloads the selected archived copy", async () => {
  render(
    <ReportEvidencePanel reportId={7} writer={false} onClose={() => {}} />,
  );
  await screen.findByRole("heading", { name: "RPT-7" });
  expect(screen.getByText("3")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Save acknowledgement" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Download report PDF" }));
  await waitFor(() =>
    expect(mocks.download).toHaveBeenCalledWith(
      "/api/reports/7/documents/doc-1/pdf",
      "report.pdf",
    ),
  );
});
it("archives the exact report version before downloading", async () => {
  mocks.api.mockImplementation(async (_path, method) =>
    method === "POST"
      ? { id: "new-doc", fileName: "new-report.pdf" }
      : evidence,
  );
  render(
    <ReportEvidencePanel reportId={7} writer={false} onClose={() => {}} />,
  );
  await screen.findByRole("heading", { name: "RPT-7" });
  fireEvent.click(screen.getByRole("button", { name: "Generate report PDF" }));
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith("/api/reports/7/documents", "POST", {
      expectedVersion: 3,
    }),
  );
  await waitFor(() =>
    expect(mocks.download).toHaveBeenCalledWith(
      "/api/reports/7/documents/new-doc/pdf",
      "new-report.pdf",
    ),
  );
});
it("records a receiver outcome with a required explanation and current version", async () => {
  render(<ReportEvidencePanel reportId={7} writer onClose={() => {}} />);
  await screen.findByRole("heading", { name: "RPT-7" });
  fireEvent.change(screen.getByLabelText("Receiver outcome"), {
    target: { value: "REJECTED" },
  });
  fireEvent.change(screen.getByLabelText("Acknowledgement reference"), {
    target: { value: "ACK-7" },
  });
  fireEvent.change(screen.getByLabelText("Acknowledgement received at"), {
    target: { value: "2026-11-14T12:00:00" },
  });
  fireEvent.change(screen.getByLabelText("Recording or correction reason"), {
    target: { value: "Manually transcribed external receiver response" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save acknowledgement" }));
  await waitFor(() =>
    expect(mocks.api).toHaveBeenCalledWith(
      "/api/reports/7/acknowledgements",
      "POST",
      expect.objectContaining({
        expectedVersion: 3,
        outcome: "REJECTED",
        reference: "ACK-7",
        reason: "Manually transcribed external receiver response",
      }),
    ),
  );
});
it("shows export errors without pretending a PDF was downloaded", async () => {
  mocks.api.mockImplementation(async (_path, method) => {
    if (method === "POST") throw Error("Report changed. Reload its details.");
    return evidence;
  });
  render(<ReportEvidencePanel reportId={7} writer onClose={() => {}} />);
  await screen.findByRole("heading", { name: "RPT-7" });
  fireEvent.click(screen.getByRole("button", { name: "Generate report PDF" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Report changed");
  expect(mocks.download).not.toHaveBeenCalled();
});
