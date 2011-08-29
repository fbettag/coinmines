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
package comet

import net.liftweb.http._
import net.liftweb.http.S._
import net.liftweb.http.SHtml._
import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._
import java.util.Date


class StatServer extends CometActor {
	def render =
		".user_hashrate *" #> "foo" &
		".user_shares_total *" #> "" &
		".user_shares_stale *" #> "" &
		".user_btc_hashrate *" #> "" &
		".user_btc_shares_total *" #> "" &
		".user_btc_shares_round *" #> "" &
		".user_btc_shares_stale *" #> "" &
		".user_btc_payout *" #> "%.8f BTC".format(50.toFloat) &
		".user_nmc_hashrate *" #> "" &
		".user_nmc_shares_total *" #> "" &
		".user_nmc_shares_round *" #> "" &
		".user_nmc_shares_stale *" #> "" &
		".user_nmc_payout *" #> "%.8f NMC".format(0.toFloat) &
		".user_slc_hashrate *" #> "" &
		".user_slc_shares_total *" #> "" &
		".user_slc_shares_round *" #> "" &
		".user_slc_shares_stale *" #> "" &
		".user_slc_payout *" #> "%.8f SLC".format(0.toFloat) &
		".global_hashrate *" #> "" &
		".global_workers *" #> "" &
		".global_btc_hashrate *" #> "" &
		".global_btc_workers *" #> "" &
		".global_btc_payout *" #> "" &
		".global_nmc_hashrate *" #> "" &
		".global_nmc_workers *" #> "" &
		".global_nmc_payout *" #> "" &
		".global_slc_hashrate *" #> "" &
		".global_slc_workers *" #> "" &
		".global_slc_payout *" #> "%.8f SLC".format(64.toFloat)
}


