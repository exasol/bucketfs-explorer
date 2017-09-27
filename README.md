# bucketfs-explorer

The BucketFS explorer is a tiny GUI written in Java/JavaFX that helps you working with BucketFS.

###### Please note that this is an open source project which is *not officially supported* by EXASOL. We will try to help you as much as possible, but can't guarantee anything since this is not an official EXASOL product.

## Prerequisites

* Java 8, BucketFS Explorer was tested with Java 8u144.
* You need to have access/credentials to EXAoperation, because a lot of the functionality is using [XMLRPC](https://github.com/EXASOL/exaoperation-xmlrpc) behind the scenes.

## Getting started

* Download jar file [bucketfsexplorer-0.0.1-SNAPSHOT-jar-with-dependencies.jar](bucketfs-explorer/build/bucketfsexplorer-0.0.1-SNAPSHOT-jar-with-dependencies.jar)
* Double-click on the jar or run java -jar bucketfsexplorer-0.0.1-SNAPSHOT-jar-with-dependencies.jar 
* Please enter the EXAoperation URL / Username / Password and the main window will be shown, use the context menu in the tree / list to get started: 

![alt text](https://github.com/EXASOL/bucketfs-explorer/blob/master/screenshots/BucketFS_Explorer_Screenshot1.PNG)

* You can also upload files via Drag N Drop from your local file browser

## Functionality

* Create / Delete / Modify BucketFS services
* Create / Delete / Modify buckets
* List files of a bucket
* Upload / Download / Delete files
* Showing additional metadata (e.g. size of a bucket, path to refer to bucket in a UDF)
* Drag N Drop files from your local file system

## Known issues

Currently no known issue, please report.
