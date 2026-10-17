import { Link, Outlet } from "react-router-dom"
import "../styles.css"

export function Layout() {
  return (
    <>
      <nav className="nav">
        <Link to="/" className="nav-brand" style={{ textDecoration: "none" }}>
          Viglet
        </Link>
        <ul className="nav-links">
          <li><Link to="/">Home</Link></li>
          <li><a href="#services">Services</a></li>
          <li><Link to="/about">About</Link></li>
        </ul>
      </nav>
      <Outlet />
      <footer className="footer">
        <p>Built with <a href="#">Viglet Shio</a> &mdash; Powered by @viglet/shio-react-sdk</p>
      </footer>
    </>
  )
}
