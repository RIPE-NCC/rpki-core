<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
    <xsl:output method="xml" standalone="omit" omit-xml-declaration="yes" indent="yes" xslt:indent-amount="2" />

    <xsl:template match='/'>
        <xsl:apply-templates select='/commands.ActivatePendingKeypairCommand'/>
        <xsl:apply-templates select='/commands.AutoKeyRolloverChildCaCommand'/>
        <xsl:apply-templates select='/commands.RevokeKeyPairCommand'/>
    </xsl:template>

    <xsl:template match="/commands.ActivatePendingKeypairCommand">
        <xsl:element name='commands.KeyManagementActivatePendingKeysCommand'>
            <xsl:copy-of select="certificateAuthorityId" />
            <xsl:copy-of select="commandGroup" />
            <xsl:element name="minStagingTimeMs">86400000</xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="/commands.AutoKeyRolloverChildCaCommand">
        <xsl:element name='commands.KeyManagementInitiateRollCommand'>
            <xsl:copy-of select="certificateAuthorityId" />
            <xsl:copy-of select="commandGroup" />
            <xsl:element name="keyAgeThreshold">0</xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="/commands.RevokeKeyPairCommand">
        <xsl:element name='commands.KeyManagementRevokeOldKeysCommand'>
            <xsl:copy-of select="certificateAuthorityId" />
            <xsl:copy-of select="commandGroup" />
        </xsl:element>
    </xsl:template>


</xsl:stylesheet>