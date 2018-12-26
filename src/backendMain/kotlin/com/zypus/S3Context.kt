package com.zypus

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.PutObjectResult
import java.io.File
import java.io.InputStream

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-09
 */
object S3Context {

    private val credentials by lazy {
        BasicAWSCredentials(System.getenv("AWS_ACCESS_KEY_ID"),
            System.getenv("AWS_SECRET_ACCESS_KEY"))
    }

    val defaultBucket by lazy {
        System.getenv("S3_BUCKET_NAME") ?: client.listBuckets()[0].name
    }

    val client by lazy {
        AmazonS3ClientBuilder
            .standard()
            .withCredentials(AWSStaticCredentialsProvider(credentials))
            .withRegion(Regions.EU_WEST_1)
            .build()
    }

    val buckets = client.listBuckets()

    val objects = client.listObjects(defaultBucket)

    fun File.upload(key: String, acl: CannedAccessControlList = CannedAccessControlList.Private, bucketName: String = defaultBucket): PutObjectResult {
        val putRequest = PutObjectRequest(bucketName, key, this)
        putRequest.withCannedAcl(acl)
        return client.putObject(putRequest)
    }

    fun InputStream.upload(key: String, meta: ObjectMetadata, bucketName: String = defaultBucket) = client.putObject(bucketName, key, this, meta)

    fun download(key: String, bucketName: String = defaultBucket) = client.getObject(bucketName, key)

    fun read(key: String, bucketName: String = defaultBucket) = download(
        key,
        bucketName
    ).objectContent as InputStream

    fun url(key: String, bucketName: String = defaultBucket) = client.getUrl(bucketName, key)

    fun delete(key: String, bucketName: String = defaultBucket) = client.deleteObject(bucketName, key)

}

fun <T> s3(block: S3Context.() -> T): T {
    return S3Context.block()
}