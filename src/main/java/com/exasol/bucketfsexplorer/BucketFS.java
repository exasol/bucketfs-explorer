package com.exasol.bucketfsexplorer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.client.ClientProtocolException;
import org.apache.xmlrpc.XmlRpcException;

public class BucketFS extends BucketObject {

	private String description;

	private Integer httpPort;

	private Integer httpsPort;

	private String disk;

	private ArrayList<Bucket> buckets;

	private Configuration config;

	private int size;

	private XmlRPCAccessLayer xmlRPC;

	public Configuration getConfig() {
		return config;
	}

	public void setConfig(Configuration config) {
		this.config = config;
	}

	public BucketFS(String bucketFSName, String description, Integer httpPort, Integer httpsPort, String disk,
			Configuration config, XmlRPCAccessLayer xmlRPC) {
		super(bucketFSName);
		this.description = description;
		this.httpPort = httpPort;
		this.httpsPort = httpsPort;
		this.disk = disk;
		this.config = config;
		this.xmlRPC = xmlRPC;

		buckets = new ArrayList<>();
	}

	
	public BucketFS(String description, Integer httpPort, Integer httpsPort, String disk,
			Configuration config, XmlRPCAccessLayer xmlRPC) {
		super("new_bucket");
		this.description = description;
		this.httpPort = httpPort;
		this.httpsPort = httpsPort;
		this.disk = disk;
		this.config = config;
		this.xmlRPC = xmlRPC;

		buckets = new ArrayList<>();
	}

	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(Integer httpPort) {
		this.httpPort = httpPort;
	}

	public Integer getHttpsPort() {
		return httpsPort;
	}

	public void setHttpsPort(Integer httpsPort) {
		this.httpsPort = httpsPort;
	}

	public void addBucket(Bucket bucket) {
		buckets.add(bucket);
	}

	public ArrayList<Bucket> getBuckets() {
		return buckets;
	}

	public boolean isNoPortSet() {
		return (httpPort == null) && (httpsPort == null);
	}

	public String getDisk() {
		return disk;
	}

	public void setDisk(String disk) {
		this.disk = disk;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public void createBucket(Bucket bucket) throws XmlRpcException {

		xmlRPC.addBucket(this.getId(), bucket.getId(), bucket.getDescription(), bucket.isPublic(),
				bucket.getReadPassword(), bucket.getWritePassword());

		buckets.add(bucket);
	}

	public XmlRPCAccessLayer getXmlRPC() {
		return xmlRPC;
	}

	public void setXmlRPC(XmlRPCAccessLayer xmlRPC) {
		this.xmlRPC = xmlRPC;
	}

	public void deleteBucket(Bucket bucket) throws XmlRpcException, KeyManagementException, ClientProtocolException, NoSuchAlgorithmException, KeyStoreException, IOException, URISyntaxException {
		
		
		bucket.deleteAllFiles();
		
		//wait until files are deleted
		while ( bucket.getFiles().size() > 0 ) {
			try {
				
				Thread.sleep(500);
				
			} catch (InterruptedException e) {
				
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
			
		xmlRPC.deleteBucket(this.getId(),bucket.getId());
		
		buckets.remove(bucket);
	}

	public void createBucketFS() throws XmlRpcException {
		
		getHttpsPort();
		
		String nameOfBucketFS = xmlRPC.addBucketFS(getDescription(), getDisk(), getHttpPort(), getHttpsPort());
		
		this.setId(nameOfBucketFS);
		
	}

	public void delete() throws XmlRpcException {
		xmlRPC.deleteBucketFS(getId());
	}

}