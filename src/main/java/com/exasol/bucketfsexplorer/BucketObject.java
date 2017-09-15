package com.exasol.bucketfsexplorer;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public abstract class BucketObject {

    public BucketObject(String id) {
        setId(id);
    }

    private final StringProperty id = new SimpleStringProperty();

    public final StringProperty idProperty() {
        return this.id;
    }

    public final String getId() {
        return this.idProperty().get();
    }

    public final void setId(final String name) {
        this.idProperty().set(name);
    }

    
    @Override
    public String toString(){
    	return this.idProperty().get();
    }
    
}