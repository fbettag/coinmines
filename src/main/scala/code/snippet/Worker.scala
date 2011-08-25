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

class Worker extends Loggable {

	def user = { User.currentUser.open_! }
	def workers = { user.workers }

	def stripName(str: String) = str.replaceAll("[^a-zA-Z0-9_-]+", "")

	/* helpers */
	def addWorker(name: String) = {
		var worker = PoolWorker.create.user(user).username(user.email + "_" + stripName(name))
		worker.save
		logger.info("New Worker %s - Name: %s".format(worker.id, worker.username))
		js.jquery.JqJsCmds.AppendHtml("worker_list", buildRow(worker))
	}


	def updateName(worker: PoolWorker, name: String) = {
			logger.info("Worker %s - New Name: %s".format(worker.id, stripName(name)))
			worker.username(user.email + "_" + name.replaceAll("[^a-zA-Z0-9_-]+", "")).save
			null
	}

	def updatePassword(worker: PoolWorker, password: String) = {
			logger.info("Worker %s - New Password".format(worker.id))
			worker.password(password).save
			null
	}

	def deleteWorker(worker: PoolWorker) = {
		logger.info("Worker %s - Delete: %s".format(worker.id, worker.username))
		worker.delete_!
		js.jquery.JqJsCmds.FadeOut("row_%s".format(worker.id), 200, 200)
	}

	/* snippets */
	def add = SHtml.ajaxText("new_worker", addWorker)

	def buildRow(worker: PoolWorker) = <xml:group>
		<tr id={"row_%s".format(worker.id)}>
			<td>{user.email}_{SHtml.ajaxText(worker.username.replaceFirst("^%s_".format(user.email), ""), updateName(worker, _))}</td>
			<td>{"%s MH/sec".format(worker.hashrate)}</td>
			<td>{worker.lasthashString}</td>
			<td>{SHtml.ajaxText(worker.password, updatePassword(worker, _), "class" -> "worker_password", "type" -> "password")}</td>
			<td>{a(() => deleteWorker(worker), <span>{S.??("delete")}</span>)}</td>
		</tr>
	</xml:group>

	def list =
		<table>
			<thead>
				<tr>
					<th>{S.??("Worker Name")}</th>
					<th>{S.??("Hashrate")}</th>
					<th>{S.??("Last Hash")}</th>
					<th>{S.??("Password")}</th>
					<th></th>
				</tr>
			</thead>
			<tbody id="worker_list">
				{workers.flatMap(buildRow _)}
			</tbody>
		</table>

}
