import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export const truncateMiddle = (
  text: string,
  maxLength: number = 10,
): string => {
  if (!text || text.length <= maxLength) return text ?? "";
  const dots = "...";
  const charsToShow = maxLength - dots.length;
  const frontChars = Math.ceil(charsToShow / 2);
  const backChars = Math.floor(charsToShow / 2);
  return text.substring(0, frontChars) + dots + text.substring(text.length - backChars);
};
