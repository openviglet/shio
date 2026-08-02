// SH285 — a widget is a catalogue KEY. `name` is the contract: the console maps it onto a
// component (WIDGET_MAP) and a post-type field references it. `className`, `implementationCode`
// and `settingPath` are gone from the backend — they named a widget class package that holds only
// constants and a `template/widget/**` directory that left with the AngularJS console.
export type ShWidget = {
  id: string;
  name: string;
  description: string;
  type: string;
};
