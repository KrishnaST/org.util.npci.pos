module org.util.npci.pos {
	
	requires transitive org.util.iso8583;
	requires transitive org.util.iso8583.npci;
	requires transitive org.util.npci.coreconnect;
	
	requires transitive retrofit2;
	requires transitive okhttp3;
	requires transitive retrofit2.converter.jackson;
	
	requires transitive com.fasterxml.jackson.core;
	requires transitive com.fasterxml.jackson.databind;
	requires transitive com.fasterxml.jackson.annotation;
}