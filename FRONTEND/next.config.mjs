/** @type {import('next').NextConfig} */
const nextConfig = {
  transpilePackages: ["@greenops/sdk"],
  experimental: { typedRoutes: true }
};

export default nextConfig;
