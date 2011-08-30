/*
 *  Copyright (c) 2011, Franz Bettag <franz@bett.ag>
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * All advertising materials mentioning features or use of this software
 *       must display the following acknowledgement:
 *       This product includes software developed by the Bettag Systems UG
 *       and its contributors.
 *
 *  THIS SOFTWARE IS PROVIDED BY BETTAG SYSTEMS UG ''AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL BETTAG SYSTEMS UG BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

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

		Schemifier.schemify(true, Schemifier.infoF _, User, AccountBalance, NetworkBlock, PoolWorker, Share)

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
