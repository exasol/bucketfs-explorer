package com.exasol.bucketfsexplorer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.xmlrpc.XmlRpcException;

public class Bucket extends BucketObject {

	private String description = null;

	private boolean isPublic = false;

	private String readPassword = null;

	private String writePassword = null;

	private BucketFS bucketFS;

	private int size;

	private String name;

	public Bucket(String id, BucketFS bFS) {
		super(id);
		this.bucketFS = bFS;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}

	public String getReadPassword() {
		return readPassword;
	}

	public void setReadPassword(String readPassword) {
		this.readPassword = readPassword;
	}

	public String getWritePassword() {
		return writePassword;
	}

	public void setWritePassword(String writePassword) {
		this.writePassword = writePassword;
	}

	public BucketFS getBucketFS() {
		return bucketFS;
	}

	public void setBucketFS(BucketFS bucketFS) {
		this.bucketFS = bucketFS;
	}

	public List<String> getFiles() throws ClientProtocolException, IOException, URISyntaxException,
			KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		String restUrl = "";

		URL exaOpURL = new URL(bucketFS.getConfig().getUrl());

		// prefer https over http
		if (bucketFS.getHttpsPort() != null)
			restUrl = "https://" + exaOpURL.getHost() + ":" + bucketFS.getHttpsPort() + "/" + getName();
		else if (bucketFS.getHttpPort() != null)
			restUrl = "http://" + exaOpURL.getHost() + ":" + bucketFS.getHttpPort() + "/" + getName();
		else 
			throw new URISyntaxException("Neither http nor https port set for Bucket FS " + bucketFS.getId(), "");

		return BucketFSAccessLayer.listFiles(restUrl, getReadPassword());
	}

	public void uploadFile(File file) throws ClientProtocolException, IOException, URISyntaxException, XmlRpcException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		String restUrl = "";

		URL exaOpURL = new URL(bucketFS.getConfig().getUrl());

		// prefer https over http
		if (bucketFS.getHttpsPort() != null)
			restUrl = "https://" + exaOpURL.getHost() + ":" + bucketFS.getHttpsPort() + "/" + getName() + "/"
					+ URLEncoder.encode(file.getName(), "UTF-8");
		else if (bucketFS.getHttpPort() != null)
			restUrl = "http://" + exaOpURL.getHost() + ":" + bucketFS.getHttpPort() + "/" + getName() + "/"
					+ URLEncoder.encode(file.getName(), "UTF-8");
		else
			throw new URISyntaxException("Neither http nor https port set for Bucket FS " + bucketFS.getId() + ".", "");

		BucketFSAccessLayer.uploadFileToBucketFS(restUrl, file.getAbsolutePath(), getWritePassword());
	}

	public void reloadMetadata() throws MalformedURLException, XmlRpcException {

		HashMap<String, Object> bucketProps = this.getBucketFS().getXmlRPC()
				.getPropertiesBucket(this.getBucketFS().getId(), this.getId());

		int bucketSize = (Integer) this.getBucketFS().getXmlRPC().getSizeOfBucket(this.getBucketFS().getId(),
				this.getId());

		this.setSize(bucketSize);

		this.setName((String) bucketProps.get("bucket_name"));

		this.setPublic((Boolean) bucketProps.get("public_bucket"));

		this.setDescription((String) bucketProps.get("description"));

	}

	public void deleteFile(String fileName)
			throws ClientProtocolException, IOException, URISyntaxException, XmlRpcException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		String restUrl = "";

		URL exaOpURL = new URL(bucketFS.getConfig().getUrl());

		// prefer https over http
		if (bucketFS.getHttpsPort() != null)
			restUrl = "https://" + exaOpURL.getHost() + ":" + bucketFS.getHttpsPort() + "/" + getName() + "/"
					+ URLEncoder.encode(fileName, "UTF-8");
		else if (bucketFS.getHttpPort() != null)
			restUrl = "http://" + exaOpURL.getHost() + ":" + bucketFS.getHttpPort() + "/" + getName() + "/"
					+ URLEncoder.encode(fileName, "UTF-8");
		else
			throw new URISyntaxException("Neither http nor https port set for Bucket FS" + bucketFS.getId(), "");

		BucketFSAccessLayer.deleteFileInBucketFS(restUrl, getWritePassword());

	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void deleteAllFiles() throws KeyManagementException, ClientProtocolException, NoSuchAlgorithmException, KeyStoreException, IOException, URISyntaxException, XmlRpcException {
		for (String f : getFiles() ) 
			deleteFile(f);
			
	}

	public void editBucket(String bucketDescNew, boolean isPublicNew) throws XmlRpcException, MalformedURLException {
		
		bucketFS.getXmlRPC().editBucket(bucketFS.getId(), getId(), bucketDescNew, isPublicNew, null, null);
		
		setDescription(bucketDescNew);
		
		setPublic(isPublicNew);
		
	}

	public void changeReadPassword(String readPasswordNew) throws MalformedURLException, XmlRpcException {
		
		bucketFS.getXmlRPC().editBucket(bucketFS.getId(), getId(), null, (Boolean) null, readPasswordNew, null);
		
		setReadPassword(readPasswordNew);
	}

	public void changeWritePassword(String writePasswordNew) throws MalformedURLException, XmlRpcException {
		
		bucketFS.getXmlRPC().editBucket(bucketFS.getId(), getId(), null, (Boolean) null, null, writePasswordNew);

		setWritePassword(writePasswordNew);
	}

}