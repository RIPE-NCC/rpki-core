<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
    <xsl:output method="text" />

    <xsl:template match="/commands.ActivateCustomerCertificateAuthorityCommand">
        <xsl:text>Created and activated Member Certificate Authority '</xsl:text>
        <xsl:value-of select="name"/>
        <xsl:text>' with resources </xsl:text>
        <xsl:for-each select="resourceClasses/class">
            <xsl:if test="number(position()) &gt; 1">
                <xsl:text>, </xsl:text>
            </xsl:if>
            <xsl:value-of select="@name"/>
            <xsl:text>=</xsl:text>
            <xsl:value-of select="translate(., '&#xA;', '')"/>
        </xsl:for-each>
    </xsl:template>

</xsl:stylesheet>