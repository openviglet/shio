/** @type {import('next').NextConfig} */
const nextConfig = {
  // Allow <Image> to load media served from the Shio instance.
  images: {
    remotePatterns: [
      { protocol: "http", hostname: "localhost" },
      // Add your Shio host here, e.g. { protocol: "https", hostname: "cms.example.com" }
    ],
  },
};

export default nextConfig;
