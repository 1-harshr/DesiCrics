import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Link } from "react-router";
import type { IconName } from "../icons.generated";
import styles from "./Buttons.module.css";
import { Icon } from "./Icon";

type Variant = "primary" | "tonal" | "ghost" | "hero" | "heroSecondary";

interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  children: ReactNode;
  icon?: IconName;
  trailingIcon?: IconName;
  loading?: boolean;
  full?: boolean;
  /** Renders a link instead of a button. */
  to?: string;
}

function makeButton(variant: Variant) {
  return function Button({ children, icon, trailingIcon, loading, full, to, className, disabled, ...rest }: ButtonProps) {
    const classes = [styles.button, styles[variant], full && styles.full, className].filter(Boolean).join(" ");
    const content = (
      <>
        {icon && <Icon name={icon} size={20} className={styles.lead} />}
        {children}
        {loading ? <span className={styles.spinner} aria-hidden /> : trailingIcon && <Icon name={trailingIcon} size={20} />}
      </>
    );
    if (to) {
      return (
        <Link to={to} className={classes}>
          {content}
        </Link>
      );
    }
    return (
      <button type="button" className={classes} disabled={disabled || loading} aria-busy={loading} {...rest}>
        {content}
      </button>
    );
  };
}

/** Brand fill: the one main action on a screen. */
export const PrimaryButton = makeButton("primary");
/** Soft fill with a brand icon, for secondary options. */
export const TonalButton = makeButton("tonal");
/** Outline, for the least important action. */
export const GhostButton = makeButton("ghost");
/** White pill: the main action inside a green hero. */
export const HeroButton = makeButton("hero");
/** Frosted pill: the second action inside a green hero. */
export const HeroSecondaryButton = makeButton("heroSecondary");

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  icon: IconName;
  label: string;
  /** Round frosted button over a green hero. */
  frosted?: boolean;
}

export function IconButton({ icon, label, frosted, className, ...rest }: IconButtonProps) {
  return (
    <button type="button" aria-label={label} title={label} className={[styles.iconButton, frosted && styles.frosted, className].filter(Boolean).join(" ")} {...rest}>
      <Icon name={icon} />
    </button>
  );
}
