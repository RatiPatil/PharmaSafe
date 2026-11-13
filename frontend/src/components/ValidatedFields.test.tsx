import { render, fireEvent, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ValidatedFields } from './ValidatedFields';
describe('native field feedback',()=>{
 it('marks invalid names and clears the error after correction without restricting narrative',()=>{
  render(<><ValidatedFields/><label>Name<input name="firstName"/></label><label>Narrative<textarea name="narrative"/></label></>);
  const name=screen.getByLabelText('Name') as HTMLInputElement;
  fireEvent.input(name,{target:{value:'Patient123'}});fireEvent.blur(name);
  expect(name.validity.customError).toBe(true);expect(name.getAttribute('aria-invalid')).toBe('true');
  fireEvent.input(name,{target:{value:'Ana María'}});fireEvent.blur(name);
  expect(name.checkValidity()).toBe(true);expect(name.hasAttribute('aria-invalid')).toBe(false);
  const text=screen.getByLabelText('Narrative') as HTMLTextAreaElement;
  fireEvent.input(text,{target:{value:'Result <10 µg/L. Day 3: follow-up.'}});expect(text.checkValidity()).toBe(true);
 });
 it('keeps email login and legacy passwords usable',()=>{
  render(<><ValidatedFields/><div className="login-page"><label>Login<input id="username"/></label><label>Secret<input name="password" type="password"/></label></div></>);
  for(const [label,value] of [['Login','safety@example.test'],['Secret','old']]){const el=screen.getByLabelText(label) as HTMLInputElement;fireEvent.input(el,{target:{value}});expect(el.checkValidity()).toBe(true);}
 });
});
