import { TextField } from "@/designsystem/components";
import { useText } from "@/i18n";

/** Filters a list by city; blank shows everywhere. */
export function CityField({ city, onChange }: { city: string; onChange: (city: string) => void }) {
  const { s } = useText();
  return (
    <TextField
      label={s("web_city_label")}
      icon="location_on"
      value={city}
      onChange={onChange}
      placeholder={s("web_city_placeholder")}
      clearLabel={s("web_city_clear")}
      autoComplete="address-level2"
      enterKeyHint="search"
    />
  );
}
