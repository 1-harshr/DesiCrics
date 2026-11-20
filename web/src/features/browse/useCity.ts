import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";

const CITY_KEY = "desicrics.city";

function remembered(): string {
  try {
    return localStorage.getItem(CITY_KEY) ?? "";
  } catch {
    return "";
  }
}

function remember(city: string) {
  try {
    if (city.trim()) localStorage.setItem(CITY_KEY, city.trim());
    else localStorage.removeItem(CITY_KEY);
  } catch {
    // Storage blocked: the address still carries it.
  }
}

/** Waits until typing settles, so a request isn't sent per keystroke. */
export function useDebounced<T>(value: T, ms = 350): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), ms);
    return () => clearTimeout(timer);
  }, [value, ms]);
  return settled;
}

/**
 * Text typed into a field that also lives in the address (`?q=…`), so the page can be shared. The field keeps
 * its own state and the address catches up once typing settles: driving the field from the address directly
 * drops keystrokes, since the address updates a moment later. Returns what's typed, a setter, and the settled
 * value to query with. When the address changes from outside (back, forward, a link), the field follows it.
 * [onSettle] should be a function that doesn't change between renders.
 */
export function useAddressText(param: string, initial: () => string = () => "", onSettle?: (value: string) => void) {
  const [params, setParams] = useSearchParams();
  const fromAddress = params.get(param);
  const [text, setText] = useState(() => fromAddress ?? initial());
  // What the address held last time we looked, and what we last wrote to it.
  const seen = useRef<string | null>(fromAddress);
  const pushed = useRef<string | null>(fromAddress);
  const settled = useDebounced(text);

  useEffect(() => {
    // Only a real change of address counts, not an effect running again with the same one.
    if (fromAddress === seen.current) return;
    seen.current = fromAddress;
    // Our own update coming back round; the field may already hold newer typing.
    if (fromAddress === pushed.current) return;
    pushed.current = fromAddress;
    setText(fromAddress ?? "");
  }, [fromAddress]);

  useEffect(() => {
    onSettle?.(settled);
    const value = settled || null;
    if (value === pushed.current) return;
    pushed.current = value;
    setParams(
      (p) => {
        const next = Object.fromEntries(p);
        if (value) next[param] = value;
        else delete next[param];
        return next;
      },
      { replace: true },
    );
  }, [settled, param, setParams, onSettle]);

  return { text, setText, settled };
}

/** The city lists are filtered by: in the address, and remembered in this browser for next time. Blank means everywhere. */
export function useCity() {
  return useAddressText("city", remembered, remember);
}
