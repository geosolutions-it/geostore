package it.geosolutions.geostore.core.model;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.bind.JAXB;

class TestMarshaller<T> {

    private final Class<T> _class;

    public TestMarshaller(Class<T> _class) {
        this._class = _class;
    }

    protected String marshal(T a) {
        StringWriter sw = new StringWriter();
        JAXB.marshal(a, sw);
        return sw.toString();
    }

    protected T unmarshal(String s) {
        StringReader sr = new StringReader(s);
        return JAXB.unmarshal(sr, _class);
    }

}
