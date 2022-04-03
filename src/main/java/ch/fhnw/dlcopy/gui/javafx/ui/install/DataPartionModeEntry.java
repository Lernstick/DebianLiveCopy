/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.fhnw.dlcopy.gui.javafx.ui.install;

import java.util.ResourceBundle;

/**
 *
 * @author user
 */
public class DataPartionModeEntry {
    private int ID;
    private String name;
    private ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");


    public DataPartionModeEntry(int id, String name) {
        this.ID = id;
        this.name = name;
    }

    public int getID() { return ID; }
    public String getName() { return name; }

    public String toString(){
        return stringBundle.getString(name);
    }
}
