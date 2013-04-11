/*
 * Author:  Filip Dvořák <filip.dvorak@runbox.com>
 *
 * Copyright (c) 2013 Filip Dvořák <filip.dvorak@runbox.com>, all rights reserved
 *
 * Publishing, providing further or using this program is prohibited
 * without previous written permission of the author. Publishing or providing
 * further the contents of this file is prohibited without previous written
 * permission of the author.
 */

package fape.model.compact;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author FD
 */
public class Reference {
    public LinkedList<String> refs = new LinkedList<>();

    @Override
    public String toString() {
        String ret = "";
        for(String s:refs){
            ret += s + ".";
        }
        ret = ret.substring(0, ret.length()-1);
        return ret;
    }
    
}
