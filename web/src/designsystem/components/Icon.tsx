import { icons, illustrations, type IconName, type IllustrationName, type VectorArt } from "../icons.generated";

function Vector({ art, size, label, className }: { art: VectorArt; size: number; label?: string; className?: string }) {
  return (
    <svg
      viewBox={art.viewBox}
      width={size}
      height={size}
      className={className}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      style={art.mirrored ? { transform: "var(--icon-mirror, none)" } : undefined}
      dangerouslySetInnerHTML={{ __html: art.body }}
    />
  );
}

/**
 * A Material Symbols icon from the app's drawables, in the colour of the text around it. Pass [label] when
 * the icon means something on its own; decorative icons stay hidden from screen readers.
 */
export function Icon({ name, size = 24, label, className }: { name: IconName; size?: number; label?: string; className?: string }) {
  return <Vector art={icons[name]} size={size} label={label} className={className} />;
}

/** Artwork drawn in its own colours (the ball mark), never tinted. */
export function Illustration({ name, size, className }: { name: IllustrationName; size: number; className?: string }) {
  return <Vector art={illustrations[name]} size={size} className={className} />;
}
