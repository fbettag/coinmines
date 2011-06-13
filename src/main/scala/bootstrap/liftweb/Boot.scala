package bootstrap.liftweb

import net.liftweb._
import util._
import Helpers._

import common._
import http._
import sitemap._
import Loc._
import mapper._

import code.model._


class Boot {
	def boot {
		if (!DB.jndiJdbcConnAvailable_?) {
		val vendor = new StandardDBVendor(Props.get("db.driver") openOr "org.postgresql.Driver",
			Props.get("db.url") openOr "jdbc:postgresql:mydatabase",
			Props.get("db.user"), Props.get("db.password"))

			LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

			DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
		}

		Schemifier.schemify(true, Schemifier.infoF _, User, AccountBalance, NetworkBlock, PoolWorker, Setting, Share, ShareHistory)

		// where to search snippet
		LiftRules.addToPackages("code")

		val redirectToHome = If(() => User.loggedIn_?, () => RedirectResponse("/"))
		val redirectToLogin = If(() => User.loggedIn_?, () => RedirectResponse("/users/login"))
		val redirectUnlessAdmin = If(() => User.isAdmin_?, () => RedirectResponse("/users/login"))

		// Build SiteMap
		def sitemap = SiteMap(
			Menu.i("Home") / "index",
			Menu.i("Admin") / "admin" >> redirectUnlessAdmin,
			Menu.i("Account") / "account" >> redirectToLogin >> User.AddUserMenusAfter,
			Menu.i("Stats") / "stats",
			Menu.i("Contact") / "contact"
			//Menu.i("") / "",

			// more complex because this menu allows anything in the
			// /static path to be visible
			//Menu(Loc("Static", Link(List("static"), true, "/static/index"), "Static Content"))
		)

		def sitemapMutators = User.sitemapMutator

		// set the sitemap.	Note if you don't want access control for
		// each page, just comment this line out.
		LiftRules.setSiteMapFunc(() => sitemapMutators(sitemap))

		// Use jQuery 1.4
		LiftRules.jsArtifacts = net.liftweb.http.js.jquery.JQuery14Artifacts

		//Show the spinny image when an Ajax call starts
		LiftRules.ajaxStart =
			Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
		
		// Make the spinny image go away when it ends
		LiftRules.ajaxEnd =
			Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

		// Force the request to be UTF-8
		LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

		// What is the function to test if a user is logged in?
		LiftRules.loggedInTest = Full(() => User.loggedIn_?)

		// Use HTML5 for rendering
		LiftRules.htmlProperties.default.set((r: Req) =>
			new Html5Properties(r.userAgent))		

		// Make a transaction span the whole HTTP request
		S.addAround(DB.buildLoanWrapper)
	}
}
