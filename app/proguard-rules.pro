# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.empowerswr.luksave.** { *; }
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
# Keep XML parsing classes and service files (for itext7-core and jackson-core)
-keep class org.w3c.dom.** { *; }
-keep class org.xml.sax.** { *; }
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
-keepattributes *Annotation*
-keepattributes *Exception*
-keepattributes Signature
# Keep BlockHound and Netty classes (for okhttp)
-keep class reactor.blockhound.** { *; }
-dontwarn reactor.blockhound.**
-keep class io.netty.util.internal.** { *; }
-dontwarn io.netty.util.internal.**

# Preserve META-INF/services files
-keep,includedescriptorclasses class * { *; }

# Keep Google Play Services location classes (for play-services-location)
-keep class com.google.android.gms.internal.location.** { *; }
-dontwarn com.google.android.gms.internal.location.**

-dontwarn com.aayushatharva.brotli4j.Brotli4jLoader
-dontwarn com.aayushatharva.brotli4j.decoder.DecoderJNI$Status
-dontwarn com.aayushatharva.brotli4j.decoder.DecoderJNI$Wrapper
-dontwarn com.aayushatharva.brotli4j.encoder.BrotliEncoderChannel
-dontwarn com.aayushatharva.brotli4j.encoder.Encoder$Mode
-dontwarn com.aayushatharva.brotli4j.encoder.Encoder$Parameters
-dontwarn com.github.luben.zstd.Zstd
-dontwarn com.github.luben.zstd.ZstdInputStreamNoFinalizer
-dontwarn com.github.luben.zstd.util.Native
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn com.google.protobuf.ExtensionRegistry
-dontwarn com.google.protobuf.ExtensionRegistryLite
-dontwarn com.google.protobuf.MessageLite$Builder
-dontwarn com.google.protobuf.MessageLite
-dontwarn com.google.protobuf.MessageLiteOrBuilder
-dontwarn com.google.protobuf.Parser
-dontwarn com.google.protobuf.nano.CodedOutputByteBufferNano
-dontwarn com.google.protobuf.nano.MessageNano
-dontwarn com.itextpdf.bouncycastle.BouncyCastleFactory
-dontwarn com.jcraft.jzlib.Deflater
-dontwarn com.jcraft.jzlib.Inflater
-dontwarn com.jcraft.jzlib.JZlib$WrapperType
-dontwarn com.jcraft.jzlib.JZlib
-dontwarn com.ning.compress.BufferRecycler
-dontwarn com.ning.compress.lzf.ChunkDecoder
-dontwarn com.ning.compress.lzf.ChunkEncoder
-dontwarn com.ning.compress.lzf.LZFChunk
-dontwarn com.ning.compress.lzf.LZFEncoder
-dontwarn com.ning.compress.lzf.util.ChunkDecoderFactory
-dontwarn com.ning.compress.lzf.util.ChunkEncoderFactory
-dontwarn com.oracle.svm.core.annotate.Alias
-dontwarn com.oracle.svm.core.annotate.InjectAccessors
-dontwarn com.oracle.svm.core.annotate.TargetClass
-dontwarn io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.AsyncTask
-dontwarn io.netty.internal.tcnative.Buffer
-dontwarn io.netty.internal.tcnative.CertificateCallback
-dontwarn io.netty.internal.tcnative.CertificateCompressionAlgo
-dontwarn io.netty.internal.tcnative.CertificateVerifier
-dontwarn io.netty.internal.tcnative.Library
-dontwarn io.netty.internal.tcnative.ResultCallback
-dontwarn io.netty.internal.tcnative.SSL
-dontwarn io.netty.internal.tcnative.SSLContext
-dontwarn io.netty.internal.tcnative.SSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.SSLSession
-dontwarn io.netty.internal.tcnative.SSLSessionCache
-dontwarn io.netty.internal.tcnative.SessionTicketKey
-dontwarn io.netty.internal.tcnative.SniHostNameMatcher
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn javax.servlet.ServletContextEvent
-dontwarn javax.servlet.ServletContextListener
-dontwarn lzma.sdk.ICodeProgress
-dontwarn lzma.sdk.lzma.Encoder
-dontwarn net.jpountz.lz4.LZ4Compressor
-dontwarn net.jpountz.lz4.LZ4Exception
-dontwarn net.jpountz.lz4.LZ4Factory
-dontwarn net.jpountz.lz4.LZ4FastDecompressor
-dontwarn net.jpountz.xxhash.XXHash32
-dontwarn net.jpountz.xxhash.XXHashFactory
-dontwarn org.apache.avalon.framework.logger.Logger
-dontwarn org.apache.log.Hierarchy
-dontwarn org.apache.log.Logger
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.xml.resolver.Catalog
-dontwarn org.apache.xml.resolver.CatalogManager
-dontwarn org.apache.xml.resolver.readers.CatalogReader
-dontwarn org.apache.xml.resolver.readers.SAXCatalogReader
-dontwarn org.eclipse.jetty.alpn.ALPN$ClientProvider
-dontwarn org.eclipse.jetty.alpn.ALPN$Provider
-dontwarn org.eclipse.jetty.alpn.ALPN$ServerProvider
-dontwarn org.eclipse.jetty.alpn.ALPN
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ClientProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$Provider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
-dontwarn org.jboss.marshalling.ByteInput
-dontwarn org.jboss.marshalling.ByteOutput
-dontwarn org.jboss.marshalling.Marshaller
-dontwarn org.jboss.marshalling.MarshallerFactory
-dontwarn org.jboss.marshalling.MarshallingConfiguration
-dontwarn org.jboss.marshalling.Unmarshaller
-dontwarn org.joda.time.Instant
-dontwarn software.amazon.awssdk.crt.CRT
-dontwarn software.amazon.awssdk.crt.auth.credentials.Credentials
-dontwarn software.amazon.awssdk.crt.auth.credentials.CredentialsProvider
-dontwarn software.amazon.awssdk.crt.auth.credentials.DelegateCredentialsHandler
-dontwarn software.amazon.awssdk.crt.auth.credentials.DelegateCredentialsProvider$DelegateCredentialsProviderBuilder
-dontwarn software.amazon.awssdk.crt.auth.credentials.DelegateCredentialsProvider
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigner
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig$AwsSignatureType
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig$AwsSignedBodyHeaderType
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig$AwsSigningAlgorithm
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningResult
-dontwarn software.amazon.awssdk.crt.http.HttpHeader
-dontwarn software.amazon.awssdk.crt.http.HttpMonitoringOptions
-dontwarn software.amazon.awssdk.crt.http.HttpProxyEnvironmentVariableSetting$HttpProxyEnvironmentVariableType
-dontwarn software.amazon.awssdk.crt.http.HttpProxyEnvironmentVariableSetting
-dontwarn software.amazon.awssdk.crt.http.HttpProxyOptions$HttpProxyAuthorizationType
-dontwarn software.amazon.awssdk.crt.http.HttpProxyOptions
-dontwarn software.amazon.awssdk.crt.http.HttpRequest
-dontwarn software.amazon.awssdk.crt.http.HttpRequestBodyStream
-dontwarn software.amazon.awssdk.crt.io.ClientBootstrap
-dontwarn software.amazon.awssdk.crt.io.EventLoopGroup
-dontwarn software.amazon.awssdk.crt.io.ExponentialBackoffRetryOptions
-dontwarn software.amazon.awssdk.crt.io.HostResolver
-dontwarn software.amazon.awssdk.crt.io.StandardRetryOptions
-dontwarn software.amazon.awssdk.crt.io.TlsCipherPreference
-dontwarn software.amazon.awssdk.crt.io.TlsContext
-dontwarn software.amazon.awssdk.crt.io.TlsContextOptions
-dontwarn software.amazon.awssdk.crt.s3.ChecksumAlgorithm
-dontwarn software.amazon.awssdk.crt.s3.ChecksumConfig$ChecksumLocation
-dontwarn software.amazon.awssdk.crt.s3.ChecksumConfig
-dontwarn software.amazon.awssdk.crt.s3.ResumeToken
-dontwarn software.amazon.awssdk.crt.s3.S3Client
-dontwarn software.amazon.awssdk.crt.s3.S3ClientOptions
-dontwarn software.amazon.awssdk.crt.s3.S3FinishedResponseContext
-dontwarn software.amazon.awssdk.crt.s3.S3MetaRequest
-dontwarn software.amazon.awssdk.crt.s3.S3MetaRequestOptions$MetaRequestType
-dontwarn software.amazon.awssdk.crt.s3.S3MetaRequestOptions$ResponseFileOption
-dontwarn software.amazon.awssdk.crt.s3.S3MetaRequestOptions
-dontwarn software.amazon.awssdk.crt.s3.S3MetaRequestProgress
-dontwarn software.amazon.awssdk.crt.s3.S3MetaRequestResponseHandler
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.JsonFactory
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.JsonGenerator
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.JsonParseException
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.JsonParser$Feature
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.JsonParser
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.JsonToken
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.TSFBuilder
-dontwarn software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature
-dontwarn sun.security.x509.AlgorithmId
-dontwarn sun.security.x509.CertificateAlgorithmId
-dontwarn sun.security.x509.CertificateSerialNumber
-dontwarn sun.security.x509.CertificateSubjectName
-dontwarn sun.security.x509.CertificateValidity
-dontwarn sun.security.x509.CertificateVersion
-dontwarn sun.security.x509.CertificateX509Key
-dontwarn sun.security.x509.X500Name
-dontwarn sun.security.x509.X509CertImpl
-dontwarn sun.security.x509.X509CertInfo