/*
 * Copyright 2011 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.jsonpath.internal;

import com.jayway.jsonpath.InvalidPathException;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Kalle Stenflo
 */
public class PathTokenizer implements Iterable<PathToken> {

    private static Pattern INVALID_PATH_PATTERN = Pattern.compile("[^\\?\\+=\\-\\*/!]\\(");

    private List<PathToken> pathTokens = new LinkedList<PathToken>();

    private char[] pathChars;
    private transient int index = 0;

    public PathTokenizer(String jsonPath) {

        if (INVALID_PATH_PATTERN.matcher(jsonPath).matches()) {
            throw new InvalidPathException("Invalid path: " + jsonPath);
        }

        if (!jsonPath.startsWith("$") && !jsonPath.startsWith("$[")) {
            jsonPath = "$." + jsonPath;
        }

        this.pathChars = jsonPath.toCharArray();


        List<String> tokens = splitPath();
        int len = tokens.size();
        int i = 0;
        for (String pathFragment : tokens) {
            pathTokens.add(new PathToken(pathFragment, i, (i==(len-1)) ));
            i++;
        }
    }

    public List<String> getFragments() {
        List<String> fragments = new LinkedList<String>();
        for (PathToken pathToken : pathTokens) {
            fragments.add(pathToken.getFragment());
        }
        return fragments;
    }

    public int size(){
        return pathTokens.size();
    }

    public String getPath() {
        return new String(pathChars);
    }
    
    public LinkedList<PathToken> getPathTokens(){
        return new LinkedList<PathToken>(pathTokens);
    }

    public Iterator<PathToken> iterator() {
        return pathTokens.iterator();
    }

    public PathToken removeLastPathToken(){
        PathToken lastPathToken = pathTokens.get(pathTokens.size() - 1);

        //TODO: this should also trim the pathChars
        pathTokens.remove(pathTokens.size() - 1);
        return lastPathToken;
    } 

    //--------------------------------------------
    //
    // Split path
    //
    //--------------------------------------------
    private boolean isEmpty() {
        return index == pathChars.length;
    }

    private char peek() {
        return pathChars[index];
    }

    private char poll() {
        char peek = peek();
        index++;
        return peek;
    }

    public List<String> splitPath() {

        List<String> fragments = new LinkedList<String>();
        while (!isEmpty()) {
            skip(' ');
            char current = peek();

            switch (current) {
                case '$':
                    fragments.add(Character.toString(current));
                    poll();
                    break;
                    
                case '.':
                    poll();
	                if (!isEmpty() && peek() == '.') {
                        poll();
                        fragments.add("..");

                        assertNotInvalidPeek('.');
                    }
                    break;

                case '[':
                    fragments.add(extract(true, ']'));
                    break;

                default:
                    fragments.add(extract(false, '[', '.'));
            }
        }

        return fragments;
    }


    private String extract(boolean includeSopChar, char... stopChars) {

        boolean escaped = false;
        StringBuilder sb = new StringBuilder();
        while (!isEmpty() && (!isStopChar(escaped, peek(), stopChars))) {

            if (peek() == '(') {
                do {
                    sb.append(poll());
                } while (peek() != ')');
                sb.append(poll());
            } else {
                char c = poll();

                if (isStopChar(escaped, c, stopChars)) {
                    if (includeSopChar) {
                        sb.append(c);
                    }
                } else if(c != '\\') {
                    sb.append(c);
                }
                
                if(escaped) escaped = false;
                else if(c == '\\') escaped = true;
            }
        }
        if (includeSopChar) {
            assertValidPeek(false, stopChars);
            sb.append(poll());
        } else {
            assertValidPeek(true, stopChars);
        }
        return clean(sb);
    }

    private String clean(StringBuilder sb) {

        String src = sb.toString();

        src = trim(src, "'");
        src = trim(src, ")");
        src = trim(src, "(");
        src = trimLeft(src, "?");
        src = trimLeft(src, "@");

        if (src.length() >= 5 && src.subSequence(0, 2).equals("['")) {
            src = src.substring(2);
            src = src.substring(0, src.length() - 2);
        }

        return src.trim();
    }

    private String trim(String src, String trim) {
        return trimLeft(trimRight(src, trim), trim);
    }

    private String trimRight(String src, String trim) {
        String scanFor = trim + " ";
        if (src.contains(scanFor)) {
            while (src.contains(scanFor)) {
                src = src.replace(scanFor, trim);
            }
        }
        return src;
    }

    private String trimLeft(String src, String trim) {
        String scanFor = " " + trim;
        if (src.contains(scanFor)) {
            while (src.contains(scanFor)) {
                src = src.replace(scanFor, trim);
            }
        }
        return src;
    }

    private boolean isStopChar(boolean escaped, char c, char... scanFor) {
        boolean found = false;
        for (char check : scanFor) {
            if (!escaped && check == c) {
                found = true;
                break;
            }
        }
    	System.out.println("isStopChar " + escaped + ", " + c + " = " + found);
        return found;
    }

    private void skip(char target) {
        if (isEmpty()) {
            return;
        }
        while (pathChars[index] == target) {
            poll();
        }
    }

    private void assertNotInvalidPeek(char... invalidChars) {
        if (isEmpty()) {
            return;
        }
        char peek = peek();
        for (char check : invalidChars) {
            if (check == peek) {
                throw new InvalidPathException("Char: " + peek + " at current position is not valid!");
            }
        }
    }

    private void assertValidPeek(boolean acceptEmpty, char... validChars) {
        if (isEmpty() && acceptEmpty) {
            return;
        }
        if (isEmpty()) {
            throw new InvalidPathException("Path is incomplete");
        }
        boolean found = false;
        char peek = peek();
        for (char check : validChars) {
            if (check == peek) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new InvalidPathException("Path is invalid");
        }
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("---------------------------------------------------------------------------").append("\n");
        sb.append("PATH: ").append(getPath()).append("\n");
        sb.append(String.format("%-50s%-10s%-10s%-10s", "Fragment", "Root", "End", "Array")).append("\n");
        sb.append("---------------------------------------------------------------------------").append("\n");
        for (PathToken pathToken : pathTokens) {
            sb.append(String.format("%-50s%-10b%-10b%-10b", pathToken.getFragment(), pathToken.isRootToken(), pathToken.isEndToken(), pathToken.isArrayIndexToken())).append("\n");
        }
        return sb.toString();

    }
}
