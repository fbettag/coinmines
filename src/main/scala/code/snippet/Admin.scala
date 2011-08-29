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

package code
package snippet

import java.util.Date
import scala.xml._

import net.liftweb._
import net.liftweb.http._
import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.SHtml._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.mapper._
import net.liftweb.common._

import model._
import lib._

class Admin extends Loggable {

	/* helpers */
	
	/* snippets */
	def bitcoins =
		".bitcoin_balance *" #> "%.8f BTC".format(try { Coind.run(BtcCmd("getbalance")).toFloat } catch { case _ => 0.0 }) &
		".bitcoin_info" #> <pre>{Coind.run(BtcCmd("getinfo"))}</pre> &
		".bitcoin_transactions" #> <pre>{Coind.run(BtcCmd("listtransactions"))}</pre>

	def namecoins =
		".namecoin_balance *" #> "%.8f NMC".format(try { Coind.run(NmcCmd("getbalance")).toFloat } catch { case _ => 0.0 }) &
		".namecoin_info" #> <pre>{Coind.run(NmcCmd("getinfo"))}</pre> &
		".namecoin_transactions" #> <pre>{Coind.run(NmcCmd("listtransactions"))}</pre>

	def solidcoins =
		".solidcoin_balance *" #> "%.8f SLC".format(try { Coind.run(SlcCmd("getbalance")).toFloat } catch { case _ => 0.0 }) &
		".solidcoin_info" #> <pre>{Coind.run(SlcCmd("getinfo"))}</pre> &
		".solidcoin_transactions" #> <pre>{Coind.run(SlcCmd("listtransactions"))}</pre>

}
