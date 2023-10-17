package datapower.ibm.com;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import datapower.ibm.com.dpfiles.Dpfile;
import datapower.ibm.com.dpfiles.Dpfiles;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.PropertyException;
import jakarta.xml.bind.Unmarshaller;



/**
 * @author Arnauld Desprets Charater '&' is special in XML if it is not escaped
 *         then the XML file is invalid!
 * 
 */
public class SOMALoadFiles {
	private String host = null;
	private boolean verbose = true;
	private boolean debug = false;
	private String action = null;
	private String filename = null;
	private String userId = null;
	private String userPwd = null;

	SSLSocketFactory factory = null;

	private final String SET_FILE_SOAP_REQUEST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<env:Envelope xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\">" + "<env:Body>"
			+ "<dp:request domain='IMPORT-DOMAIN' xmlns:dp=\"http://www.datapower.com/schemas/management\">" + "<dp:set-file name=\"FILE-NAME\">" + "FILE-CONTENTS" + "</dp:set-file>" + "</dp:request>" + "</env:Body>" + "</env:Envelope>";

	private final int httpConnectionTimeOut = 5000;

	public SOMALoadFiles(String[] args) {
		if (!parmsAreValid(args) | (host == null)) {
			System.out.println("Usage: java SOMALoadFiles" + "\n -action [load | create] " + "\n -host <ip:port> " + "\n [-FOLDER <path to folder> | -FILE <path to file>]" + "\n -userId <userID> -userPwd <password>\n\n\n");
			System.exit(1);
		}
	}

	/**
	 * 
	 */
	private void loadAllFiles() {
		try {
			long start = System.currentTimeMillis();
			Dpfiles allFiles = getFilesFromManifest();
			int cnt = 1;
			for (int i = 0; i <allFiles.getDpfile().size(); i++) {
				Dpfile dpfile=allFiles.getDpfile().get(i);
				File f = new File(dpfile.getContent());
				System.out.println(cnt + " Loading file : " + f.getName() + " into domain " + dpfile.getDomain());
				int rc = loadFile(dpfile.getDomain(), dpfile.getDestination(), f);
				if (rc != 0) {
					break;
				}
				cnt++;
			}// endfor
			long duration = System.currentTimeMillis() - start;
			if (verbose) {
				System.out.println("Load took " + duration + " ms");
			}
		} catch (InvalidManifestException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * 
	 */
	private Dpfiles getFilesFromManifest() throws InvalidManifestException {
		Dpfiles rFiles = null;
		try {
			JAXBContext jaxbCtx = JAXBContext.newInstance("datapower.ibm.com.dpfiles");
			InputStream is = new FileInputStream(filename);
			Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
			rFiles = (Dpfiles) unmarshaller.unmarshal(is);
		} catch (PropertyException e) {
			throw new InvalidManifestException(e.getMessage());
		} catch (FileNotFoundException e) {
			throw new InvalidManifestException(e.getMessage());
		} catch (JAXBException e) {
			System.out.println("Error when checking file " + filename + " : " + e.toString());
			throw new InvalidManifestException(e.getMessage());
		}
		return rFiles;
	}

	/*
	 * Read in the XML config file containing the configuration to import onto
	 * the specified device, and add it to the SOAP request.
	 */
	private int loadFile(String domain, String destination, File file) {
		int rc = 0;
		FileInputStream fis = null;

		try {
			String dpRequest = SET_FILE_SOAP_REQUEST;
			dpRequest = dpRequest.replaceFirst("IMPORT-DOMAIN", domain);
			String destinationFileName = destination + "/" + file.getName();
			dpRequest = dpRequest.replaceFirst("FILE-NAME", destinationFileName);

			// Get the file to be loaded
			fis = new java.io.FileInputStream(file);
			byte[] buffer = new byte[fis.available()];
			int read = fis.read(buffer);
			fis.close();
			if (debug) {
				System.out.println("File" + file.getName() + " size: " + read);
			}
			
			String  encodedContent = Base64.getEncoder().encodeToString(buffer);
			dpRequest = dpRequest.replaceFirst("FILE-CONTENTS", encodedContent);
			rc = submitSOMARequest(dpRequest);

		} catch (Exception e) {
			System.out.println("\nError processing XML file " + file.getName() + "\n");
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
			System.exit(1);
		}
		return rc;
	}

	/**
	 * 
	 * @return
	 */
	private SSLSocketFactory getSSLSocketFactory() {
		if (factory == null) {
			try {
				// Please be aware that the use of TLSv1.2 is really needed. If you use TLSv1 it will not work with IBM JRE. The problem happens when the server side uses large DH key (e.g. 2048 bit) in TLSv1/TLSv1.1 key exchange 
				SSLContext sc = SSLContext.getInstance("TLSv1.2");
				sc.init(null, trustAllCerts, null);
				factory = sc.getSocketFactory();
			} catch (KeyManagementException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return factory;
	}

	/*
	 * Setup all the SSL stuff, create the HTTP connections and submit the SOAP
	 * request
	 */

	private int submitSOMARequest(String request) {
		int rc = 0;
		String line = null;
		String hostURL = "";

		try {
			hostURL = "https://" + host + "/service/mgmt/current";
			HttpsURLConnection.setDefaultSSLSocketFactory(getSSLSocketFactory());
			HttpsURLConnection urlc = (HttpsURLConnection) (new URL(hostURL)).openConnection();

			urlc.setHostnameVerifier(new MyHostNameVerifier());
			String authString = userId + ":" + userPwd;

			String encoding = Base64.getEncoder().encodeToString(authString.getBytes());
			urlc.setConnectTimeout(httpConnectionTimeOut);
			urlc.setRequestProperty("Authorization", "Basic " + encoding);
			urlc.setUseCaches(false);
			urlc.setDoOutput(true);
			urlc.setDoInput(true);
			urlc.setRequestMethod("POST");
			urlc.setRequestProperty("SOAPAction", "SOMALoadFiles");
			urlc.setRequestProperty("content-type", "text/xml; charset=utf-8");

			OutputStream out = urlc.getOutputStream();
			Writer wout = new OutputStreamWriter(out);
			wout.write(request);
			wout.flush();
			wout.close();

			StringBuffer sb = new StringBuffer();
			BufferedReader in = new BufferedReader(new InputStreamReader(urlc.getInputStream()));
			boolean responseOK = true;
			for (;;) {
				line = in.readLine();
				if (line == null || line.equals("") || line.equals(null)) {
					break;
				} else {
					// Very simple check to see if the response is OK
					if (line.contains("result")) {
						if (!line.contains("OK")) {
							responseOK = false;
						}
						// We do not store the all response
						sb.append(line);
					}
				}
			}
			in.close();
			urlc.getInputStream().close();
			// urlc.disconnect();
			if (!responseOK) {
				System.out.println(sb.toString());
				System.out.println("Response from DataPower contains error.");
				rc = 1;
			}
		} catch (MalformedURLException e1) {
			System.out.println("\nError submitting SOMA Request1!");
			System.out.println(e1.toString());
			System.out.println(e1.getLocalizedMessage());
		} catch (ProtocolException e1) {
			System.out.println("\nError submitting SOMA Request2!");
			System.out.println(e1.toString());
			System.out.println(e1.getLocalizedMessage());
		} catch (SocketTimeoutException e1) {
			rc = 1;
			System.out.println("\nError submitting SOMA Request2!");
			System.out.println(e1.toString());
			System.out.println(e1.getLocalizedMessage());
		} catch (IOException e1) {
			System.out.println("\nError submitting SOMA Request3!");
			System.out.println(e1.toString());
			System.out.println(e1.getLocalizedMessage());
			e1.printStackTrace();
		}
		return rc;
	}

	private boolean parmsAreValid(String[] args) {

		for (int x = 0; x < args.length; x++) {
			if (args[x].equals("-action") && args.length > x) {
				action = (args[x + 1]);
			}
			if (args[x].equals("-host") && args.length > x) {
				host = (args[x + 1]);
			}
			if (args[x].equals("-FOLDER") && args.length > x) {
				filename = (args[x + 1]);
			}
			if (args[x].equals("-FILE") && args.length > x) {
				filename = (args[x + 1]);
			}
			if (args[x].equals("-userId") && args.length > x) {
				userId = (args[x + 1]);
			}
			if (args[x].equals("-userPwd") && args.length > x) {
				userPwd = (args[x + 1]);
			}
			if (args[x].equals("-verbose")) {
				verbose = true;
			}
			if (args[x].equals("-debug")) {
				debug = true;
			}
			if (args[x].equals("-?") || args[x].equals("-help") || args[x].equals("--?") || args[x].equals("--h")) {
				return false;
			}
		}
		// Check files and other arguments
		if (action == null) {
			System.out.println("Parameter action is empty");
			return false;
		} else {
			if (!action.equals("load") && !action.equals("create")) {
				System.out.println("Parameter action is either load or create");
				return false;
			}
		}

		if (filename != null) {
			File tF = new File(filename);
			if (!tF.exists()) {
				System.out.println(filename + " does not exist");
				return false;
			}
		} else {
			System.out.println("Parameter FILE is empty");
			return false;
		}
		if (userId == null) {
			System.out.println("Parameter userId is empty");
			return false;
		}
		if (userPwd == null) {
			System.out.println("Parameter userPwd is empty");
			return false;
		}
		return true;
	}

	public static void main(String[] args) {
		SOMALoadFiles loader = new SOMALoadFiles(args);
		loader.loadAllFiles();
		System.out.println("Done");
		return;
	}

	/**
	 * To trust any certificate, to simplify the management.
	 */
	public TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
		public X509Certificate[] getAcceptedIssuers() {
			if (debug) {
				System.out.println("Return the list of accepted issuers. (null)");
			}
			return null;
		}

		public void checkClientTrusted(X509Certificate[] certs, String authType) {
		}

		public void checkServerTrusted(X509Certificate[] certs, String authType) {
		}
	} };

	/**
	 * To ignore that the URL's hostname and the server's identification
	 * hostname mismatch, during SSL/TLS handshaking.
	 */
	public class MyHostNameVerifier implements HostnameVerifier {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}
}