import { useId, type InputHTMLAttributes, type ReactNode } from "react";
import type { IconName } from "../icons.generated";
import { IconButton } from "./Buttons";
import styles from "./Forms.module.css";
import { Icon } from "./Icon";

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> {
  label?: string;
  icon?: IconName;
  value: string;
  onChange: (value: string) => void;
  /** Shows a clear button while there's text; [clearLabel] is what it says to screen readers. */
  clearLabel?: string;
}

/** A labelled field with an optional leading icon and clear button, as DesiTextField. */
export function TextField({ label, icon, value, onChange, clearLabel, className, ...rest }: TextFieldProps) {
  const id = useId();
  return (
    <div className={[styles.field, className].filter(Boolean).join(" ")}>
      {label && (
        <label htmlFor={id} className={styles.label}>
          {label}
        </label>
      )}
      <div className={styles.box}>
        {icon && <Icon name={icon} />}
        <input id={id} className={styles.input} value={value} onChange={(e) => onChange(e.target.value)} {...rest} />
        {clearLabel && value && <IconButton icon="close" label={clearLabel} onClick={() => onChange("")} />}
      </div>
    </div>
  );
}

/** Match cards and the like, as many to a row as fit. */
export function CardGrid({ children }: { children: ReactNode }) {
  return <div className={styles.grid}>{children}</div>;
}
