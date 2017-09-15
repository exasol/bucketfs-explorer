package com.exasol.bucketfsexplorer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

@SuppressWarnings("deprecation")
public class BucketFSAccessLayer {

	// uploadFileToBucketFS("http://192.168.106.131:2580/bucket1/original-virtualschema-jdbc-adapter-dist-0.0.1-SNAPSHOT.jar",
	// "C:\\Users\\tb\\Desktop\\github-repos\\virtual-schemas\\jdbc-adapter\\virtualschema-jdbc-adapter-dist\\target\\original-virtualschema-jdbc-adapter-dist-0.0.1-SNAPSHOT.jar","bucket1");

	public static void uploadFileToBucketFS(String url, String filePath, String password)
			throws ClientProtocolException, IOException, URISyntaxException {

		HttpClient httpClient = HttpClientBuilder.create().build();
		URIBuilder uriBuilder = new URIBuilder(url);
		HttpPut request = new HttpPut(uriBuilder.build());

		String auth = "w:" + password;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
		String authHeader = "Basic " + new String(encodedAuth);
		request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);

		FileEntity fileEntity = new FileEntity(new File(filePath));

		request.setEntity(fileEntity);

		HttpResponse response = httpClient.execute(request);

		if (response.getStatusLine().getStatusCode() != 200)
			throw new IOException(response.toString());

	}

	public static void deleteFileInBucketFS(String url, String password)
			throws ClientProtocolException, IOException, URISyntaxException {

		HttpClient httpClient = HttpClientBuilder.create().build();
		URIBuilder uriBuilder = new URIBuilder(url);
		HttpDelete request = new HttpDelete(uriBuilder.build());

		String auth = "w:" + password;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
		String authHeader = "Basic " + new String(encodedAuth);
		request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);

		HttpResponse response = httpClient.execute(request);

		if (response.getStatusLine().getStatusCode() != 200)
			throw new IOException(response.toString());
	}

	@SuppressWarnings("deprecation")
	public static List<String> listFiles(String url, String password) throws ClientProtocolException, IOException,
			URISyntaxException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

		SSLContextBuilder builder = new SSLContextBuilder();
		builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(builder.build(),
				NoopHostnameVerifier.INSTANCE);
		org.apache.http.config.Registry<ConnectionSocketFactory> registry = RegistryBuilder
				.<ConnectionSocketFactory>create().register("http", new PlainConnectionSocketFactory())
				.register("https", sslConnectionSocketFactory).build();

		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
		cm.setMaxTotal(100);
		CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory)
				.setConnectionManager(cm).build();

		URIBuilder uriBuilder = new URIBuilder(url);
		HttpGet request = new HttpGet(uriBuilder.build());

		String auth = "r:" + password;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
		String authHeader = "Basic " + new String(encodedAuth);
		request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);

		HttpResponse response = httpClient.execute(request);

		if (response.getStatusLine().getStatusCode() != 200)
			throw new IOException(response.toString());

		BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

		ArrayList<String> fileList = new ArrayList<>();

		String output;

		while ((output = br.readLine()) != null)
			fileList.add(output);

		return fileList;
	}

}