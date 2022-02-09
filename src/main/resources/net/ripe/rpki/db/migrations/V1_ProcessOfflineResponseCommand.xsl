<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
  <xsl:output method="xml" standalone="omit" omit-xml-declaration="yes" indent="yes" xslt:indent-amount="2" />

  <xsl:template match='/'>
    <xsl:element name='commands.ProcessTrustAnchorResponseCommand'>
      <xsl:apply-templates select='/commands.ProcessOfflineResponseCommand'/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="/commands.ProcessOfflineResponseCommand">
    <xsl:copy-of select="certificateAuthorityId"/>
    <xsl:copy-of select="commandGroup"/>
    <xsl:element name="response">
      <xsl:apply-templates select="response"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="response">
    <xsl:element name="requestCreationTimestamp"><xsl:value-of select="requestCreationTimeMillis"/></xsl:element>
    <xsl:apply-templates select="rtaSigningResponse | trustAnchorResponse"/>
    <xsl:copy-of select="rtaSigningResponse/publishedObjects | trustAnchorResponse/publishedObjects"/>
  </xsl:template>

  <xsl:template match="rtaSigningResponse | trustAnchorResponse">
    <xsl:choose>
      <xsl:when test="count(newResourceCertificates/node()) = 0 and count(encodeRevokedPubKeys/node()) = 0">
        <xsl:element name="taResponses"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:element name="taResponses">
          <xsl:if test="count(newResourceCertificates/node()) &gt; 0">
            <xsl:element name="SigningResponse">
              <xsl:element name="requestId">76b01bef-651f-45ab-85ae-e356e7632c1a</xsl:element>
              <xsl:copy-of select="newResourceCertificates"/>
            </xsl:element>
          </xsl:if>
          <xsl:if test="count(encodeRevokedPubKeys/node()) &gt; 0">
            <xsl:element name="RevocationResponse">
              <xsl:element name="requestId">76b01bef-651f-45ab-85ae-e356e7632c1a</xsl:element>
              <xsl:element name="encodedRevokedPublicKeys">
                <xsl:copy-of select="encodeRevokedPubKeys/*"/>
              </xsl:element>
            </xsl:element>
          </xsl:if>
        </xsl:element>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
