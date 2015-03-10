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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import server.nanohttpd.NanoHTTPD;
import static server.nanohttpd.NanoHTTPD.MIME_HTML;
import static server.nanohttpd.NanoHTTPD.MIME_PLAINTEXT;

/**
 *
 */
public class WebServer extends NanoHTTPD {

	private final String ROOT_DIR = "./webRootFolder";

	private final String API_SUBDIR = "/scormcloud";

	private Map<String, String> mimeTypes = new LinkedHashMap<String, String>();

	private final String[] MAIN
			= {
				"/", ""
			};

	private final String XSS_KEY = "Access-Control-Allow-Origin";
	private final String XSS_VALUE = "*";

	//serve pages
	private final String PAGE_TO_SERVE = ROOT_DIR + "/scormcloud.html";

	private String mimeType = MIME_PLAINTEXT;
	
	private class NumberCollection {
		public String id;
		public ArrayList<String> numbers;
	}
	
	public WebServer() {
		super(Settings.port());
		mimeTypes.put("js", "application/javascript");
		mimeTypes.put("html", MIME_HTML);
		mimeTypes.put("htm", MIME_HTML);
		mimeTypes.put("json", "application/json");
	}
	
	@Override
    public Response serve(String uri, Method method, Map<String, String> headers, Map<String, String> params,
                                   Map<String, String> files) {
//	public NanoHTTPD.Response serve(NanoHTTPD.HTTPSession session) {
		Response response = new Response("");
		response.addHeader(XSS_KEY, XSS_VALUE);

		String responseStr = "";
		switch (method) {
			case GET:
			case POST:

				//=== PARSE URI===
				if (main.Main.DEBUG) {
					System.out.println("Unparsed: " + method + " '" + uri + "' ");
				}
				if (uri.endsWith("/"))//remove the last slash on a uri
				{
					uri = uri.substring(0, uri.length() - 1);
				}
				if (uri.startsWith(API_SUBDIR + "/")) {
					uri = uri.replaceFirst(API_SUBDIR, "");
				}
				if (main.Main.DEBUG) {
					System.out.println("Parsed:   " + method + " '" + uri + "' ");
				}
				//===END PARSE URI===

				if (uri.equals(MAIN[0]) || uri.equals(MAIN[1])) {
					try {
//                    return new NanoHTTPD.Response(NanoHTTPD.Response.Status.ACCEPTED, MIME_HTML, new FileInputStream(PAGE_TO_SERVE));//
						response.setData(new FileInputStream(PAGE_TO_SERVE));
						response.setMimeType(MIME_HTML);
						response.setStatus(Response.Status.OK);
						return response;
					} catch (FileNotFoundException ex) {
						Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
						String fail = "<body><h2>Internal error</h2><p>Unable to load " + PAGE_TO_SERVE + "</p></body>";
						response.setData(new ByteArrayInputStream(fail.getBytes()));
						response.setMimeType(MIME_HTML);
						response.setStatus(Response.Status.OK);
						return response;
//                    return new NanoHTTPD.Response(NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_HTML, "Failed to load page.");
					}
				} else if (uri.startsWith("/launch")) {
					// launch is how you get the URL to the SCORM player.  The parameters are:
					// - key: license key (see scormcloud.xml for license keys)
					// - lid: unique learner id
					// - email: email of the learner
					// - first: first name of learner
					// - last: last name of learner
					// - cid: course ID to register
					// - exiturl: optional URL for exiting of launcher
					// - cssurl: optional CSS URL
					//
					// this call will check if the user is already registered or not for the course, and if not,
					// it will register them.  From there it will then determine the launch url and return that
					// back in the JSON payload.  Response will contain:
					// - url: launch url for the registered user
					// - error: string describing the failure (if failure)
					if (Settings.validateKey(params.get("key"))) {
						String owner = Settings.keyOwner(params.get("key"));
						String lid = params.get("lid");
						String email = params.get("email");
						String first = params.get("first");
						String last = params.get("last");
						String cid = params.get("cid");
						String exiturl = params.get("exiturl");
						String cssurl = params.get("cssurl");

						ScormManager.launch(response, params, lid, email, first, last, cid, exiturl, cssurl); 
						return response;
					} else {
						JSONObject json = new JSONObject();
						json.put("error", "Invalid key");
						System.err.println("REJECT: Invalid key " + params.get("key") + " on number " + params.get("number"));
						setJSONResponse(response, params, json, Response.Status.UNAUTHORIZED);
						return response;
					}
				} else if (uri.startsWith("/results")) {
					// results is how you lookup the current course results for a specific user.  Parameters are:
					// - key: license key
					// - lid: id of the learner
					// - cid: id of the course (optional; if not supplied, all courses registered by the learner are returned)
					//
					// returns:
					// - courses: array of all courses registered for the user, with detailed information about each course
					// - error: string describing the failure (if error)
					if (Settings.validateKey(params.get("key"))) {
						String owner = Settings.keyOwner(params.get("key"));
						String lid = params.get("lid");
						String cid = params.get("cid");

						ScormManager.results(response, params, lid, cid); 
						return response;
					} else {
						JSONObject json = new JSONObject();
						json.put("error", "Invalid key");
						System.err.println("REJECT: Invalid key " + params.get("key") + " on number " + params.get("number"));
						setJSONResponse(response, params, json, Response.Status.UNAUTHORIZED);
						return response;
					}
				} else //standard file
				{
					System.out.println("URI: " + uri);
					String[] pieces = uri.split("\\.");
					String extension = null;

					if (pieces.length != 0) {
						extension = pieces[pieces.length - 1];
					}
					if (main.Main.DEBUG) {
						System.out.print("Extension: " + extension + " ");
					}

					if (extension != null && mimeTypes.get(extension) != null) {
						mimeType = mimeTypes.get(extension);
					} else {
						mimeType = MIME_PLAINTEXT;
					}
					System.out.println("MimeType: " + mimeType);

					try {
						response.setData(new FileInputStream(ROOT_DIR + uri));
						response.setMimeType(mimeType);
						response.setStatus(Response.Status.OK);
						return response;
					} catch (FileNotFoundException ex) {
						System.err.println("File not found: " + ROOT_DIR + uri);
						JSONObject json = new JSONObject();
						json.put("error", "Unknown API request: '" + ROOT_DIR + uri + "' not found");
						setJSONResponse(response, params, json, Response.Status.NOT_FOUND);
						return response;
					}
				}
				// unreachable...
			default:
				System.err.println("Invalid request (not GET or POST)");
				JSONObject json = new JSONObject();
				json.put("error", "Invalid request (not GET or POST)");
				setJSONResponse(response, params, json, Response.Status.BAD_REQUEST);
				return response;
		}
	}
	
	/*
	 * setJSONResponse: Handles the json response, including support for JSON and JSONP based on the callback query string
	*/
	static public void setJSONResponse(Response response, Map<String, String> params, JSONObject json, Response.Status status) {
		String jsonStr = json.toJSONString();
		String callback = params.get("callback");
		if (callback != null) {
			response.setMimeType("application/javascript");
			if (callback.isEmpty())
				callback = "jsonCallback";
			jsonStr = callback + "( " + jsonStr + ");";
		} else
			response.setMimeType("application/json");
		response.setData(new ByteArrayInputStream(jsonStr.getBytes()));
		response.setStatus(status);			
	} 

}
