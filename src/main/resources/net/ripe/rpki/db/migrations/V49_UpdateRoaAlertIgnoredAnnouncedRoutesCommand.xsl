<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
    <xsl:output method="text" />

    <xsl:template match="/commands.UpdateRoaAlertIgnoredAnnouncedRoutesCommand">
        <xsl:text>Updated suppressed routes for ROA alerts. Additions: </xsl:text>
        <xsl:if test="number(count(additions/AnnouncedRoute)) = 0">
            <xsl:text>none</xsl:text>
        </xsl:if>
        <xsl:for-each select="additions/AnnouncedRoute">
            <xsl:if test="number(position()) &gt; 1">
                <xsl:text>, </xsl:text>
            </xsl:if>
            <xsl:text>[asn=</xsl:text>
            <xsl:value-of select="originAsn"/>
            <xsl:text>, prefix=</xsl:text>
            <xsl:value-of select="prefix"/>
            <xsl:text>]</xsl:text>
        </xsl:for-each>
        <xsl:text>. Deletions: </xsl:text>
        <xsl:if test="number(count(deletions/AnnouncedRoute)) = 0">
            <xsl:text>none</xsl:text>
        </xsl:if>
        <xsl:for-each select="deletions/AnnouncedRoute">
            <xsl:if test="number(position()) &gt; 1">
                <xsl:text>, </xsl:text>
            </xsl:if>
            <xsl:text>[asn=</xsl:text>
            <xsl:value-of select="originAsn"/>
            <xsl:text>, prefix=</xsl:text>
            <xsl:value-of select="prefix"/>
            <xsl:text>]</xsl:text>
        </xsl:for-each>
        <xsl:text>.</xsl:text>
    </xsl:template>

</xsl:stylesheet>