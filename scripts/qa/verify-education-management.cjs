// Exercises actual management screens against the disposable educational dataset.
module.exports = async function ({
  domainAdmin: domain,
  collegeAdmin: college,
  otherAdmin,
  student,
  alpha,
  one,
  raw,
  qaDirectory,
}) {
  const fs = require("node:fs"),
    path = require("node:path");
  try {
    const nav = (p, label) =>
      p
        .getByRole("navigation", { name: "College workspace navigation" })
        .getByRole("button", { name: label, exact: true });
    async function home(p) {
      if (
        await p
          .getByRole("navigation", { name: "College workspace navigation" })
          .count()
      )
        await nav(p, "All seven domains").click();
      await p.locator(".edu-domain-card").last().waitFor();
      if (await p.locator("#education-navigation").count())
        throw Error("No sidebar is permitted on admin domain selection");
      if ((await p.locator(".edu-domain-card").count()) !== 7)
        throw Error("Exactly seven selection cards required");
    }
    async function open(p) {
      await home(p);
      await p
        .getByRole("button", { name: "Open Case Processor", exact: true })
        .click();
      await p
        .getByRole("heading", { name: "Work status distribution", exact: true })
        .waitFor();
    }
    await nav(student, "All seven domains").click();
    await student
      .getByRole("button", { name: "Open Case Processor", exact: true })
      .click();
    await student
      .getByRole("heading", { name: "Work status distribution", exact: true })
      .waitFor();
    for (const [p, name] of [
      [domain, "domain-admin"],
      [college, "college-admin"],
    ]) {
      await home(p);
      for (const width of [1440, 390]) {
        await p.setViewportSize({ width, height: 900 });
        if (
          await p.evaluate(
            () => document.documentElement.scrollWidth > innerWidth + 1,
          )
        )
          throw Error("Selection overflow");
        await p.screenshot({
          path: path.join(
            qaDirectory,
            "screens",
            name + "-selection-" + width + ".png",
          ),
          fullPage: true,
        });
      }
      await p.setViewportSize({ width: 1440, height: 1000 });
    }
    await open(domain);
    await domain
      .getByRole("button", { name: "Clear workspace filters", exact: true })
      .click();
    await nav(domain, "Work Management").click();
    await domain
      .getByRole("heading", { name: "College work orders", exact: true })
      .waitFor();
    const order = domain.getByRole("form", {
      name: "Create college work",
      exact: true,
    });
    await order
      .getByLabel("Work college", { exact: true })
      .selectOption(String(alpha.id));
    for (const [label, value] of Object.entries({
      "College work title": "QA college source review",
      "College work description":
        "Review synthetic source evidence for case intake.",
      "College work instructions":
        "Document the four minimum criteria with source reasoning.",
      "College work deadline": new Date(Date.now() + 86400000)
        .toISOString()
        .slice(0, 16),
    }))
      await order.getByLabel(label, { exact: true }).fill(value);
    await order
      .getByRole("button", { name: "Create college work", exact: true })
      .click();
    let row = domain
      .getByRole("row")
      .filter({ hasText: "QA college source review" });
    await row
      .getByRole("button", { name: "Edit college work", exact: true })
      .click();
    let dialog = domain.getByRole("dialog", { name: "Edit work", exact: true });
    await dialog
      .getByLabel("Work title", { exact: true })
      .fill("QA clarified college source review");
    await dialog
      .getByLabel("Work change reason", { exact: true })
      .fill("Clarified title before distribution");
    await dialog
      .getByRole("button", { name: "Save work changes", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    await domain
      .getByRole("row")
      .filter({ hasText: "QA clarified college source review" })
      .waitFor();
    await open(college);
    await nav(college, "Work Management").click();
    row = college
      .getByRole("row")
      .filter({ hasText: "QA clarified college source review" });
    await row
      .getByRole("button", {
        name: "Assign college work to students",
        exact: true,
      })
      .click();
    await college
      .getByRole("form", { name: "Assign student work", exact: true })
      .waitFor();
    if (
      (await college.getByLabel("Task title", { exact: true }).inputValue()) !==
      "QA clarified college source review"
    )
      throw Error("Work order must prefill original instructions");
    await college
      .getByLabel("Recipients", { exact: true })
      .selectOption("SELECTED");
    await college.getByLabel("Selected Student IDs", { exact: true }).fill(one);
    await college
      .getByRole("button", { name: "Assign domain work", exact: true })
      .click();
    await college
      .getByRole("row")
      .filter({ hasText: "QA clarified college source review" })
      .filter({
        has: college.getByRole("button", {
          name: "Open student assignment",
          exact: true,
        }),
      })
      .waitFor();
    const task = (
      await raw(college, "/api/education/tasks?domain=ROLE_CASE_PROCESSOR")
    ).value.content.find(
      (t) => t.title === "QA clarified college source review",
    );
    if (!task?.sourceOrderId)
      throw Error("Dispatched work must retain source order");
    if (
      (
        await raw(
          otherAdmin,
          "/api/education/orders?domain=ROLE_CASE_PROCESSOR&collegeId=" +
            alpha.id,
        )
      ).status !== 403
    )
      throw Error("Cross-college order access");
    await nav(domain, "Colleges").click();
    row = domain.getByRole("row").filter({ hasText: "QA-ALPHA" });
    await row
      .getByRole("button", { name: "Edit college", exact: true })
      .click();
    let form = domain.getByRole("form", {
      name: "Manage college",
      exact: true,
    });
    await form
      .getByLabel("College name", { exact: true })
      .fill("Synthetic Learning College Alpha Updated");
    await form
      .getByLabel("Change reason", { exact: true })
      .fill("Verified college display name");
    await form
      .getByRole("button", { name: "Save college", exact: true })
      .click();
    await domain
      .getByRole("cell", {
        name: "Synthetic Learning College Alpha Updated",
        exact: false,
      })
      .waitFor();
    form = domain.getByRole("form", { name: "Manage college", exact: true });
    await form.getByLabel("College code", { exact: true }).fill("QA-REMOVE");
    await form
      .getByLabel("College name", { exact: true })
      .fill("Synthetic Archive College");
    await form
      .getByRole("button", { name: "Add college", exact: true })
      .click();
    row = domain.getByRole("row").filter({ hasText: "QA-REMOVE" });
    await row
      .getByRole("button", { name: "Remove college", exact: true })
      .click();
    dialog = domain.getByRole("dialog", {
      name: "Confirm college removal",
      exact: true,
    });
    await dialog
      .getByRole("button", { name: "Confirm college removal", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    await row
      .getByRole("button", { name: "Restore college", exact: true })
      .waitFor();
    await nav(domain, "Students").click();
    await domain
      .getByLabel("Student profile", { exact: true })
      .selectOption({ label: "Synthetic College Student · " + one });
    form = domain.getByRole("form", { name: "Manage student", exact: true });
    await form
      .getByLabel("Student full name", { exact: true })
      .fill("Synthetic Student Updated");
    await form
      .getByLabel("Student change reason", { exact: true })
      .fill("Verified student identity correction");
    await form
      .getByRole("button", { name: "Save student profile", exact: true })
      .click();
    await domain
      .getByRole("row")
      .filter({ hasText: one })
      .getByRole("cell", { name: "Synthetic Student Updated", exact: false })
      .waitFor();
    await domain
      .getByRole("button", { name: "Add student", exact: true })
      .click();
    form = domain.getByRole("form", { name: "Manage student", exact: true });
    await form
      .getByLabel("Student college", { exact: true })
      .selectOption(String(alpha.id));
    for (const [label, value] of Object.entries({
      "Student full name": "Synthetic Removal Student",
      "Student email": "remove.student.ui@example.test",
      "Student username": "remove.student.ui",
      "Initial student password": "SyntheticRemovalPassword123!",
      "Student change reason": "Verified manual student enrollment",
    }))
      await form.getByLabel(label, { exact: true }).fill(value);
    await form
      .getByRole("button", { name: "Create student", exact: true })
      .click();
    await domain
      .getByLabel("Student profile", { exact: true })
      .locator("option")
      .filter({ hasText: "Synthetic Removal Student" })
      .waitFor({ state: "attached" });
    const option = domain
      .getByLabel("Student profile", { exact: true })
      .locator("option")
      .filter({ hasText: "Synthetic Removal Student" });
    const sid = await option.getAttribute("value");
    await domain
      .getByLabel("Student profile", { exact: true })
      .selectOption(sid);
    form = domain.getByRole("form", { name: "Manage student", exact: true });
    await form
      .getByRole("button", { name: "Remove student", exact: true })
      .click();
    dialog = domain.getByRole("dialog", {
      name: "Confirm student removal",
      exact: true,
    });
    await dialog
      .getByRole("button", { name: "Confirm student removal", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    const profile = (
      await raw(
        domain,
        "/api/education/students/" + sid + "?domain=ROLE_CASE_PROCESSOR",
      )
    ).value;
    if (profile.active) throw Error("Student removal must revoke access");
    if (profile.domains.length !== 7)
      throw Error("All seven associations must remain stored");
    await nav(domain, "Work Management").click();
    row = domain
      .getByRole("row")
      .filter({ hasText: "QA clarified college source review" })
      .filter({
        has: domain.getByRole("button", {
          name: "Open student assignment",
          exact: true,
        }),
      });
    await row
      .getByRole("button", { name: "Edit or remove work", exact: true })
      .click();
    dialog = domain.getByRole("dialog", { name: "Edit work", exact: true });
    await dialog
      .getByLabel("Work change reason", { exact: true })
      .fill("Withdraw unused student assignment, preserve order link");
    await dialog
      .getByRole("button", { name: "Remove work", exact: true })
      .click();
    await dialog
      .getByRole("button", { name: "Confirm work removal", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    if (!(await raw(domain, "/api/education/tasks/" + task.id)).value.archived)
      throw Error("Work removal must archive the task");
    await nav(college, "Settings").click();
    await college
      .getByRole("checkbox", { name: "Reduce animations", exact: true })
      .check();
    await college
      .getByRole("button", { name: "Save workspace preferences", exact: true })
      .click();
    await college.getByText("Saved successfully.", { exact: true }).waitFor();
    if (!(await raw(college, "/api/education/preferences")).value.reduceMotion)
      throw Error("Preferences must persist");
    await nav(student, "Profile").click();
    await student
      .getByRole("heading", { name: "My profile", exact: true })
      .waitFor();
    await student
      .locator(".edu-profile")
      .getByText("Synthetic Student Updated", { exact: true })
      .waitFor();
    console.log(
      "PASS sidebar-free Domain/College selection, role-specific selected-domain dashboards, real college/student create/edit/removal, college work-order editing and handoff, selected-student dispatch, immutable source link, confirmed archive, cross-college denial, persistent settings and own student profile",
    );
  } catch (e) {
    console.log(
      "Domain screen: " +
        (await domain.locator("body").innerText()).slice(0, 7000),
    );
    console.log(
      "College screen: " +
        (await college.locator("body").innerText()).slice(0, 4000),
    );
    throw e;
  }
};
