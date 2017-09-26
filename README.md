# bucketfs-explorer

The BucketFS explorer is a tiny GUI written in Java/JavaFX that helps you working with BucketFS.

###### Please note that this is an open source project which is *not yet officially supported* by EXASOL. We will try to help you as much as possible, but can't guarantee anything since this is not an official EXASOL product.

## Prerequisites

* Java 1.8 BucketFS Explorer was tested with Java 8u144.
* You need to have credentials for EXAoperation, because a lot of the functionality is using [XMLRPC] (https://github.com/EXASOL/exaoperation-xmlrpc) behind the scenes.

## Getting started

* Download jar file [bucketfsexplorer-0.0.1-SNAPSHOT-jar-with-dependencies.jar](bucketfs-explorer/build/bucketfsexplorer-0.0.1-SNAPSHOT-jar-with-dependencies.jar)
* Double-click on the jar or run java -jar bucketfsexplorer-0.0.1-SNAPSHOT-jar-with-dependencies.jar 
* Please enter the EXAopertaion URL / Username / Password and the Main Window will be shown:

![alt text](https://github.com/EXASOL/bucketfs-explorer/blob/master/screenshots/BucketFS_Explorer_Screenshot1.PNG)


## Functionality

* Create / Delete / Modify BucketFS services
* Create / Delete / Modify buckets
* List files in of a bucket
* Upload / Download / Delete files

## Known issue

Currently no known issue, please report.
