import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";

const key = "desicrics.city";

function remembered(): string {
  try {
    return localStorage.getItem(key) ?? "";
  } catch {
    return "";
  }
}

/**
 * The city lists are filtered by, kept in the address (`?city=Pune`) so a filtered page can be shared, and
 * remembered in this browser for next time. Blank means everywhere.
 */
export function useCity(): [string, (city: string) => void] {
  const [params, setParams] = useSearchParams();
  const fromUrl = params.get("city");

  // Coming back without a city in the address: start from the one used last.
  useEffect(() => {
    const last = remembered();
    if (fromUrl == null && last) setParams((p) => ({ ...Object.fromEntries(p), city: last }), { replace: true });
  }, [fromUrl, setParams]);

  const setCity = (city: string) => {
    try {
      if (city.trim()) localStorage.setItem(key, city.trim());
      else localStorage.removeItem(key);
    } catch {
      // Storage blocked: the address still carries it.
    }
    setParams(
      (p) => {
        const next = Object.fromEntries(p);
        if (city) next.city = city;
        else delete next.city;
        return next;
      },
      { replace: true },
    );
  };

  return [fromUrl ?? remembered(), setCity];
}

/** Waits until typing settles, so a request isn't sent per keystroke. */
export function useDebounced<T>(value: T, ms = 350): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), ms);
    return () => clearTimeout(timer);
  }, [value, ms, setSettled]);
  return settled;
}
