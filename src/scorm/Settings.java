package scorm;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import main.Main;
import org.xml.sax.SAXException;

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
/**
 *
 * @author Chris
 */
public class Settings {

	static HashMap<String, String> keys = new HashMap<>();
	static int port = 80;
	static String endpoint = "";
	static String appid = "";
	static String secret = "";
	static String keystore = "";
	static String keysecret = "";
	
	public static void loadSettings() {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			org.w3c.dom.Document document = db.parse(new File("scormcloud.xml"));

			XPathFactory xpathFactory = XPathFactory.newInstance();
			XPath xpath = xpathFactory.newXPath();

			for (int keyIndex = 1; true; ++keyIndex) {
				String key = xpath.evaluate("scormcloud/keys/key[" + keyIndex + "]", document);
				if (key.isEmpty()) {
					break;
				}
				String owner = xpath.evaluate("scormcloud/keys/key[" + keyIndex + "]/@owner", document);
				if (Main.DEBUG)
					System.out.println("Key is " + key + " for " + owner);
				keys.put(key, owner);
			}
			
			// get the server settings
			port = Integer.valueOf(xpath.evaluate("scormcloud/server/port", document));
			keystore = xpath.evaluate("scormcloud/server/keystore", document);
			keysecret = xpath.evaluate("scormcloud/server/secret", document);
			
			// now pick up cloud settings
			endpoint = xpath.evaluate("scormcloud/cloud/endpoint", document);
			appid = xpath.evaluate("scormcloud/cloud/appid", document);
			secret = xpath.evaluate("scormcloud/cloud/secret", document);

		} catch (ParserConfigurationException e) {
			System.out.println("Parser error " + e.getMessage());
		} catch (SAXException e) {
			System.out.println("SAX error " + e.getMessage());
		} catch (IOException e) {
			System.out.println("IO error " + e.getMessage());
		} catch (XPathExpressionException e) {
			System.out.println("XPath error " + e.getMessage());
		}
	}
	
	public static boolean validateKey(String key) {
		return keys.containsKey(key);
	}
	
	public static String keyOwner(String key) {
		return keys.get(key);
	}
	
	public static int port() {
		return port;
	}
	
	public static String endpoint() {
		return endpoint;
	}

	public static String appid() {
		return appid;
	}

	public static String secret() {
		return secret;
	}
	
	public static String keystore() {
		return keystore;
	}
	
	public static String keysecret() {
		return keysecret;
	}
}
