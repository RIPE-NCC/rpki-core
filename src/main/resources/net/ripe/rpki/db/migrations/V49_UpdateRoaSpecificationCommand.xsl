<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
    <xsl:output method="text" />

    <xsl:template match="/commands.UpdateRoaSpecificationCommand">
        <xsl:text>Updated ROA specification '</xsl:text>
        <xsl:value-of select="roaSpecificationData/name"/>
        <xsl:text>' [asn=</xsl:text>
        <xsl:value-of select="roaSpecificationData/asn"/>
        <xsl:text>, prefixes=</xsl:text>
        <xsl:for-each select="roaSpecificationData/prefixes/RoaPrefix">
            <xsl:if test="number(position()) &gt; 1">
                <xsl:text>, </xsl:text>
            </xsl:if>
            <xsl:value-of select="prefix"/>
            <xsl:text> maximumLength=</xsl:text>
            <xsl:choose>
                <xsl:when test="maximumLength">
                    <xsl:value-of select="maximumLength"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="substring-after(prefix, '/')"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:for-each>
        <xsl:text>]. </xsl:text>
        <xsl:if test="roaSpecificationData/validityPeriod/notValidBefore">
            <xsl:text>Not valid before: </xsl:text>
            <xsl:value-of select="roaSpecificationData/validityPeriod/notValidBefore"/>
        </xsl:if>
        <xsl:if test="roaSpecificationData/validityPeriod/notValidAfter">
            <xsl:text> Not valid after: </xsl:text>
            <xsl:value-of select="roaSpecificationData/validityPeriod/notValidAfter"/>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>