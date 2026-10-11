import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./apps/**/*.{ts,tsx}", "./packages/**/*.{ts,tsx}"],
  theme: { extend: {} },
  plugins: []
};

export default config;
