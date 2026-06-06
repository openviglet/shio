import type { ImgHTMLAttributes } from "react";

export interface ShioImageProps
  extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src"> {
  /** File field value — an absolute URL or a path relative to {@link baseUrl}. */
  src: string | null | undefined;
  /** Prefix for relative `src` values (e.g. the Shio instance URL). */
  baseUrl?: string;
}

/** Renders a File/image field value, resolving relative paths against `baseUrl`. */
export function ShioImage({ src, baseUrl, alt = "", ...rest }: ShioImageProps) {
  if (!src) return null;
  const isAbsolute = /^(https?:)?\/\//.test(src) || src.startsWith("data:");
  const resolved =
    isAbsolute || !baseUrl
      ? src
      : `${baseUrl.replace(/\/+$/, "")}/${src.replace(/^\/+/, "")}`;
  // eslint-disable-next-line jsx-a11y/alt-text
  return <img src={resolved} alt={alt} {...rest} />;
}
