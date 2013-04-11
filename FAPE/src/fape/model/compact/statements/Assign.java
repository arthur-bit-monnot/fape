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
package fape.model.compact.statements;

import fape.model.compact.Reference;

/**
 *
 * @author FD
 */
public class Assign extends Statement {

    public String operator;
    public Reference from, to;

    @Override
    public String toString() {
        if(operator.equals(":=")){
            return interval + " " + leftRef + " " + operator + " " + from;
        }else{
            return interval + " " + leftRef + " " + operator + " " + from + " :-> " + to;
        }
        
    }
}
