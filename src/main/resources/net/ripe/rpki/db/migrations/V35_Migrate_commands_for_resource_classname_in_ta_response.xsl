<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
    <xsl:output method="xml" standalone="omit" omit-xml-declaration="yes" indent="yes" xslt:indent-amount="2" />

    <xsl:template match='/'>
        <xsl:element name='commands.ProcessTrustAnchorResponseCommand'>
            <xsl:apply-templates select='/commands.ProcessTrustAnchorResponseCommand'/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="/commands.ProcessTrustAnchorResponseCommand">
        <xsl:copy-of select="certificateAuthorityId"/>
        <xsl:copy-of select="commandGroup"/>
        <xsl:element name="response">
            <xsl:apply-templates select="response"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="response">
        <xsl:copy-of select="requestCreationTimestamp"/>
        <xsl:element name="taResponses">
            <xsl:apply-templates select="taResponses"/>
        </xsl:element>
        <xsl:copy-of select="publishedObjects"/>
    </xsl:template>

    <xsl:template match="taResponses">
        <xsl:apply-templates select="SigningResponse"/>
        <xsl:apply-templates select="RevocationResponse"/>
    </xsl:template>

    <xsl:template match="SigningResponse">
        <xsl:element name="SigningResponse">
            <xsl:copy-of select="requestId"/>
            <xsl:element name="resourceClassName">RIPE</xsl:element>
            <xsl:element name="publicationUri"><xsl:value-of select="newResourceCertificates/entry/uri"></xsl:value-of></xsl:element>
            <xsl:element name="certificate">
                <xsl:copy-of select="newResourceCertificates/entry/X509ResourceCertificate/encoded" />
            </xsl:element>
        </xsl:element>

    </xsl:template>

    <xsl:template match="RevocationResponse">
        <xsl:element name="RevocationResponse">
            <xsl:copy-of select="requestId"/>
            <xsl:element name="resourceClassName">RIPE</xsl:element>
            <xsl:element name="encodedPublicKey"><xsl:value-of select="encodedRevokedPublicKeys/string"></xsl:value-of></xsl:element>
        </xsl:element>

    </xsl:template>



</xsl:stylesheet>
