import rules from './inputRules.json';
/** The server and form share the same supported registration choices. */
export const registrationOptions:Record<string,string[]> = rules.registrationChoices;
