/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        neonCyan: '#00E5FF',
        neonAmber: '#FFAB00',
        neonPurple: '#B388FF',
        darkBg: '#0B0C10',
        darkCard: '#151722'
      }
    },
  },
  plugins: [],
}
