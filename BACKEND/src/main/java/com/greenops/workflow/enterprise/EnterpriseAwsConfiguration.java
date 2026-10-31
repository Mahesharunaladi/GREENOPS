package com.greenops.workflow.enterprise;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.rds.RdsAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
public class EnterpriseAwsConfiguration {
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    DynamoDbAsyncClient enterpriseDynamoDbClient(@Value("${greenops.aws.region:us-east-1}") String region) {
        return DynamoDbAsyncClient.builder().region(Region.of(region)).build();
    }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    S3AsyncClient enterpriseS3Client(@Value("${greenops.aws.region:us-east-1}") String region) {
        return S3AsyncClient.builder().region(Region.of(region)).build();
    }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    Ec2AsyncClient enterpriseEc2Client(@Value("${greenops.aws.region:us-east-1}") String region) {
        return Ec2AsyncClient.builder().region(Region.of(region)).build();
    }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    RdsAsyncClient enterpriseRdsClient(@Value("${greenops.aws.region:us-east-1}") String region) {
        return RdsAsyncClient.builder().region(Region.of(region)).build();
    }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    IamAsyncClient enterpriseIamClient() { return IamAsyncClient.builder().region(Region.US_EAST_1).build(); }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    EventBridgeAsyncClient enterpriseEventBridgeClient(@Value("${greenops.aws.region:us-east-1}") String region) {
        return EventBridgeAsyncClient.builder().region(Region.of(region)).build();
    }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    CloudWatchAsyncClient enterpriseCloudWatchClient(@Value("${greenops.aws.region:us-east-1}") String region) {
        return CloudWatchAsyncClient.builder().region(Region.of(region)).build();
    }
}
