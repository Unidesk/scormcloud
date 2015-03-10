/*
 * Copyright (C) 2015 Unidesk Corporation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package scorm;

import com.rusticisoftware.hostedengine.client.Configuration;
import com.rusticisoftware.hostedengine.client.RegistrationService;
import com.rusticisoftware.hostedengine.client.ScormEngineService;
import com.rusticisoftware.hostedengine.client.datatypes.Enums;
import com.rusticisoftware.hostedengine.client.datatypes.RegistrationData;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import server.nanohttpd.NanoHTTPD.Response;

/**
 *
 * @author cmidgley
 */
public class ScormManager {
	private static Configuration config = null;
	private static ScormEngineService service = null;
	private static RegistrationService registration = null;
    
	private static void init() {
		if (config == null) {
			// initialize the scorm cloud library
			config = new Configuration(Settings.endpoint, Settings.appid, Settings.secret, "unidesk.scormcloud.1");
			service = new ScormEngineService(Settings.endpoint, Settings.appid, Settings.secret, "unidesk.scormcloud.1");
			registration = new RegistrationService(config, service);
		}
	}
	
	public static void launch(Response response, Map<String, String> params, String lid, String email, String first, String last, String cid, String exiturl, String cssurl) {
		System.err.println("LAUNCH: " + lid + "/" + cid + ", for " + first + " " + last + " (" + email + ")");
		JSONObject json = new JSONObject();
		init();
		try {
			// build up the registration id from the combination of course id and learner id
			String rid = cid + "-" + lid;
			// are they already registered?
			if (!registration.RegistrationExists(rid)) {
				// user is not registered for this course - go ahead and register them
				registration.CreateRegistration(rid, cid, lid, first, last, email);
			}
			// now get the launch url
			String url = registration.GetLaunchUrl(rid, exiturl, cssurl);

			// and formulate the success json response
			json.put("url", url);
			
			WebServer.setJSONResponse(response, params, json, Response.Status.OK);
		} catch (Exception e) {
			System.err.println("   ERROR: exception caught: " + e.getMessage());
			json.put("error", "EXCEPTION: " + e.getMessage());			
			WebServer.setJSONResponse(response, params, json, Response.Status.INTERNAL_ERROR);
		}
	}
	
	public static void results(Response response, Map<String, String> params, String lid, String cid) {
		System.err.println("RESULTS: " + lid + "/" + cid);
		
		JSONObject json = new JSONObject();
		JSONArray jsonClasses = new JSONArray();
		
		// init the SCORM Cloud inteface, if not already initialized
		init();
		
		try {
			List<RegistrationData> regs;
			
			regs = registration.GetRegistrationListResults(cid, lid, null, null, Enums.RegistrationResultsFormat.COURSE_SUMMARY, null, null);
			for (RegistrationData reg : regs) {
				String courseId = reg.getCourseId();

				// is this one we care about?  If user supplied a course ID and it's not our course Id, skip this one
				if ( (cid != null) && (!cid.isEmpty()) && (!cid.equals(courseId)) )
					continue;
				
				// capture the values
				String courseTitle = reg.getCourseTitle();
				int lastCourseVersionLaunched = reg.getLastCourseVersionLaunched();
				Date completedDate = reg.getCompletedDate();
				Date firstAccessDate = reg.getFirstAccessDate();
				Date lastAccessDate = reg.getLastAccessDate();
				
				String xmlResults = reg.getResultsData();
				
				// 
				// xmlResults is a string with XML that looks like this:
				// <registrationreport format="course" instanceid="0" regid="UD102-L2">
				//     <complete>complete</complete>
				//     <success>passed</success>
				//     <totaltime>254</totaltime>
				//     <score>100</score>
				// </registrationreport>

				// parse the XML results into a document
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setNamespaceAware(true);
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document xmldoc = builder.parse(new InputSource(new StringReader(xmlResults)));

				// now locate the key values from the xmldoc
				String complete = xmldoc.getElementsByTagName("complete").item(0).getFirstChild().getTextContent();
				String success = xmldoc.getElementsByTagName("success").item(0).getFirstChild().getTextContent();
				String totalTime = xmldoc.getElementsByTagName("totaltime").item(0).getFirstChild().getTextContent();
				String score = xmldoc.getElementsByTagName("score").item(0).getFirstChild().getTextContent();
				
				// now add to the json array
				SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				JSONObject jsonClass = new JSONObject();
				jsonClass.put("cid", courseId);
				jsonClass.put("courseTitle", courseTitle);
				jsonClass.put("lastCourseVersionLaunched", lastCourseVersionLaunched);
				// completedDate is "" when not yet completed, even though other dates may be filled in
				jsonClass.put("completedDate", (completedDate == null) ? "" : dateFormatter.format(completedDate));
				jsonClass.put("firstAccessDate", (firstAccessDate == null) ? "" : dateFormatter.format(firstAccessDate));
				jsonClass.put("lastAccessDate", (lastAccessDate == null) ? "" : dateFormatter.format(lastAccessDate));
				
				// complete is "incomplete" even when ending is completed.  not sure what complete means
				// goes "complete" on successful test completion independent of watching everything
				// but score is set from "unknown" to a value - so you can see that (even though values for success
				// are in scorm and not visible in this api)
				jsonClass.put("complete", complete);	// status can be "unknown", "incomplete", "complete"
				jsonClass.put("success", success);	// "unknown", or "passed".  Have not yet seen "failed".  "unknown" on failed test?
				jsonClass.put("totalTime", totalTime); // "0" or number of seconds (as a string)
				jsonClass.put("score", score); // "unknown", or... # (100 is perfect score)
					
				jsonClasses.add(jsonClass);
				System.err.println("   Course: " + courseTitle + ", version=" + lastCourseVersionLaunched + ", complete=" + complete + ", success=" + success + ", totaltime=" + totalTime + ", score=" + score);
			}
			
			// and formulate the success json response
			json.put("classes", jsonClasses);
			WebServer.setJSONResponse(response, params, json, Response.Status.OK);
		} catch (Exception e) {
			System.err.println("   ERROR: exception caught: " + e.getMessage());
			json.put("error", "EXCEPTION: " + e.getMessage());			
			WebServer.setJSONResponse(response, params, json, Response.Status.INTERNAL_ERROR);
		}
	}	
}
