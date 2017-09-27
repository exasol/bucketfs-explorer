package com.exasol.bucketfsexplorer;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.xmlrpc.*;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

public class XmlRPCAccessLayer {

	Configuration exaoperationConfig;

	private XmlRpcClient client;

	static {
		try {
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {

				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					// Trust always
				}

				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					// Trust always
				}

				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

			} };

			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			// Create empty HostnameVerifier
			HostnameVerifier hv = new HostnameVerifier() {

				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);

		} catch (Exception ex) {

		}
	}

	public XmlRPCAccessLayer(Configuration exaoperationConfig) throws MalformedURLException {

		this.exaoperationConfig = exaoperationConfig;

		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		config.setServerURL(new URL(exaoperationConfig.getUrl() + "/cluster1"));
		config.setBasicUserName(exaoperationConfig.getUsername());
		config.setBasicPassword(exaoperationConfig.getPassword());

		client = new XmlRpcClient();
		client.setConfig(config);
		client.setTypeFactory(new XmlRpcTypeNil(client)); // accept nil type
															// from EXASOL
															// XMLRPC python
															// server
	}

	public Object listMethods() throws XmlRpcException {
		return client.execute("listMethods", new Object[] {});
	}

	public Object listObjects() throws XmlRpcException {
		return client.execute("listObjects", new Object[] {});
	}

	public Object[] listObjects(String object) throws XmlRpcException {
		return (Object[]) client.execute(object + ".listObjects", new Object[] {});
	}

	public HashMap<String, Object> getPropertiesBucketFS(String bucketFSname) throws XmlRpcException {

		return (HashMap<String, Object>) client.execute(bucketFSname + ".getProperties", new Object[] {});
	}

	public Integer getSizeOfBucketFS(String bucketFSname) throws XmlRpcException {

		Object ret = client.execute(bucketFSname + ".getSize", new Object[] {});
		
		if ( ret instanceof Integer)
			return (Integer)ret;
		
		else
			return new Integer(-1);
		
	}

	public HashMap<String, Object> getPropertiesBucket(String bucketFSname, String bucket)
			throws XmlRpcException, MalformedURLException {

		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		config.setServerURL(new URL(exaoperationConfig.getUrl() + "/cluster1/" + bucketFSname));
		config.setBasicUserName(exaoperationConfig.getUsername());
		config.setBasicPassword(exaoperationConfig.getPassword());

		XmlRpcClient myClient = new XmlRpcClient();
		myClient.setConfig(config);
		myClient.setTypeFactory(new XmlRpcTypeNil(myClient)); // accept nil type
																// from EXASOL
																// XMLRPC python
																// server

		return (HashMap<String, Object>) myClient.execute(bucket + ".getProperties", new Object[] {});
	}

	public Object getSizeOfBucket(String bucketFSname, String bucket) throws XmlRpcException, MalformedURLException {

		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		config.setServerURL(new URL(exaoperationConfig.getUrl() + "/cluster1/" + bucketFSname));
		config.setBasicUserName(exaoperationConfig.getUsername());
		config.setBasicPassword(exaoperationConfig.getPassword());

		XmlRpcClient myClient = new XmlRpcClient();
		myClient.setConfig(config);
		myClient.setTypeFactory(new XmlRpcTypeNil(myClient)); // accept nil type
																// from EXASOL
																// XMLRPC python
																// server

		return myClient.execute(bucket + ".getSize", new Object[] {});
	}

	public Object[] listBucketFSs() throws XmlRpcException {

		Object[] objects = (Object[]) listObjects();

		ArrayList<String> bucketFSs = new ArrayList<String>();

		for (int i = 0; i < objects.length; i++) {
			if (objects[i].toString().startsWith("bucket") || objects[i].toString().startsWith("bfsdefault"))
				bucketFSs.add(objects[i].toString());
		}

		return bucketFSs.toArray();
	}

	
	//  Method addBucketFS
	//        Create a new object type BucketFS
	//
	//    Function takes a dictionary with parameters.
	//    Allowed keys are (required fields are marked  with * ):
	//      description, type: TextLine, Some description of this BucketFS.
	//    * disk, type: Choice, Disk for BucketFS data.
	//      http_port, type: Int, Port for FS access.
	//      https_port, type: Int, Port for SSL encrypted FS access.
	
	public String addBucketFS( String description, String disk, Integer httpPort, Integer httpsPort) throws XmlRpcException {

		Map<String, Comparable> map = new HashMap<String, Comparable>();

		map.put("description", description);
		map.put("disk", disk);
		
		if(httpPort != null)
			map.put("http_port", httpPort);
		
		if(httpsPort != null)
			map.put("https_port", httpsPort);

		Object[] params = new Object[] { map };

		return (String) client.execute("addBucketFS", params);
	}

	
	//	  Method editBucketFS
	//	         Edits object.
	//
	//	    Function take a dictionary with parameters and return a list of fields, that was modified.
	//	    Allowed keys are (required fields are marked  with * ):
	//	      description, type: TextLine, Some description of this BucketFS.
	//	    * disk, type: Choice, Disk for BucketFS data.
	//	      http_port, type: Int, Port for FS access.
	//	      https_port, type: Int, Port for SSL encrypted FS access.
	
	public Object editBucketFS(String bucketFSName, String description, String disk, Integer httpPort, Integer httpsPort) throws XmlRpcException {

		Map<String, Comparable> map = new HashMap<String, Comparable>();

		map.put("description", description);
		map.put("disk", disk);
		
		if(httpPort != null)
			map.put("http_port", httpPort);
		
		if(httpsPort != null)
			map.put("https_port", httpsPort);

		Object[] params = new Object[] { map };

		return client.execute(bucketFSName+".editBucketFS", params);
	}

	
	
	/*
	 * SERVICE BucketFS Object:
	 * https://<user>:<pass>@<license_server>/cluster1/<object_name> Method
	 * addBucket Create a new object type BucketFSBucket
	 * 
	 * Function takes a dictionary with parameters. Allowed keys are (required
	 * fields are marked with * ): bucket_name, type: TextLine, The name of
	 * bucket. description, type: TextLine, Some description of this BucketFS
	 * Bucket. public_bucket, type: Bool, Public buckets require no password for
	 * reading. read_password, type: Password, Password readonly access.
	 * write_password, type: Password, Password for write access.
	 */

	public void addBucket(String bucketFS, String bucketName, String description, boolean publicBucket,
			String readPassword, String writePassword) throws XmlRpcException {
		Map<String, Comparable> map = new HashMap<String, Comparable>();

		map.put("bucket_name", bucketName);
		map.put("description", description);
		map.put("public_bucket", publicBucket);
		map.put("read_password", readPassword);
		map.put("write_password", writePassword);

		Object[] params = new Object[] { map };

		client.execute(bucketFS + ".addBucket", params);
	}

	
	//
	//SERVICE BucketFSBucket
	//Object: https://<user>:<pass>@<license_server>/cluster1/<object_name>
	//  Method editBucketFSBucket
	//         Edits object.
	//
	//    Function take a dictionary with parameters and return a list of fields, that was modified.
	//    Allowed keys are (required fields are marked  with * ):
	//    * bucket_name, type: TextLine, The name of bucket.
	//      description, type: TextLine, Some description of this BucketFS Bucket.
	//    * public_bucket, type: Bool, Public buckets require no password for reading.
	//    * read_password, type: Password, Password readonly access.
	//    * write_password, type: Password, Password for write access.

	public void editBucket(String bucketFS, String bucketName, String description, Boolean publicBucket,
			String readPassword, String writePassword) throws XmlRpcException, MalformedURLException {
		Map<String, Comparable> map = new HashMap<String, Comparable>();

		map.put("bucket_name", bucketName);
		
		if(description != null)
			map.put("description", description);
		
		if(publicBucket != null)
			map.put("public_bucket", publicBucket);
		
		if(readPassword != null)
			map.put("read_password", readPassword);
		
		if(writePassword != null)
			map.put("write_password", writePassword);

		Object[] params = new Object[] { map };

		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		config.setServerURL(new URL(exaoperationConfig.getUrl() + "/cluster1/" + bucketFS));
		config.setBasicUserName(exaoperationConfig.getUsername());
		config.setBasicPassword(exaoperationConfig.getPassword());

		XmlRpcClient myClient = new XmlRpcClient();
		myClient.setConfig(config);
		myClient.setTypeFactory(new XmlRpcTypeNil(myClient)); // accept nil type
																// from EXASOL
																// XMLRPC python
																// server

		myClient.execute(bucketName + ".editBucketFSBucket", params);
	}

	
	public void deleteBucket(String bucketFS, String bucketName) throws XmlRpcException {

		ArrayList<String> para = new ArrayList<>();
		
		para.add(bucketName);
		
		client.execute(bucketFS + ".deleteSubObject", para);
		
	}

	public void deleteBucketFS(String bucketFSName) throws XmlRpcException {
		ArrayList<String> para = new ArrayList<>();
		
		para.add(bucketFSName);
		
		client.execute("deleteSubObject", para);
		
	}

}