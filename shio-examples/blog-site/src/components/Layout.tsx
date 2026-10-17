import { Link, Outlet } from "react-router-dom"
import "../styles.css"

export function Layout() {
  return (
    <>
      <nav className="nav">
        <Link to="/" className="nav-brand">
          Tech<span>Blog</span>
        </Link>
        <ul className="nav-links">
          <li><Link to="/">Articles</Link></li>
          <li><a href="#">Featured</a></li>
        </ul>
      </nav>
      <Outlet />
      <footer className="footer">
        <p>Built with <a href="#">Viglet Shio</a> &mdash; Powered by @viglet/shio-react-sdk</p>
      </footer>
    </>
  )
}
