<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:m="http://www.loc.gov/MARC21/slim" exclude-result-prefixes="m">
    <xsl:output indent="yes"/>

    <xsl:template match="/">
        <node id="NUT" label="Ngā Upoko Tukutuku / Māori Subject Headings">
            <isComposedBy>
                <xsl:apply-templates select="m:collection/m:record[not(m:datafield[@tag='550']/m:subfield[@code='w']/text()='g')][m:datafield[@tag='550']/m:subfield[@code='w']/text()='h']"/>
            </isComposedBy>
        </node>
    </xsl:template>

    <xsl:template match="m:record">
        <node>
            <xsl:attribute name="label">
                <xsl:value-of select="m:datafield[@tag='150']/m:subfield[@code='a']/text()"/>
                <xsl:if test="m:datafield[@tag='450'][1]/m:subfield[@code='a']/text()!=''">
                    <xsl:text> | </xsl:text>
                    <xsl:apply-templates select="m:datafield[@tag='450']" mode="see-also"/>
                </xsl:if>
            </xsl:attribute>
            <xsl:attribute name="id">
                <xsl:value-of select="m:datafield[@tag='150']/m:subfield[@code='a']/text()"/>
            </xsl:attribute>
            <xsl:if test="m:datafield[@tag='550'][m:subfield[@code='w' and text()='h']]">
                <isComposedBy>
                    <xsl:for-each select="m:datafield[@tag='550'][m:subfield[@code='w' and text()='h']]">
                        <xsl:call-template name="subterm">
                            <xsl:with-param name="term">
                                <xsl:value-of select="m:subfield[@code='a']/text()"/>
                            </xsl:with-param>
                        </xsl:call-template>
                    </xsl:for-each>
                </isComposedBy>
            </xsl:if>
        </node>
    </xsl:template>

    <xsl:template name="subterm">
        <xsl:param name="term"/>
        <xsl:apply-templates select="//m:record[m:datafield[@tag='150']/m:subfield[@code='a']/text()=$term]"/>
    </xsl:template>

    <xsl:template mode="see-also" match="m:datafield">
        <xsl:value-of select="m:subfield[@code='a']/text()"/>
        <xsl:if test="following-sibling::m:datafield[@tag='450'][m:subfield[@code='a' and text() !='']]">
            <xsl:text>; </xsl:text>
        </xsl:if>
    </xsl:template>

    <xsl:template match="*"/>

</xsl:stylesheet>