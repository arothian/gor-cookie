# gor-cookie
Gor middleware(https://github.com/buger/gor#middleware) for automatically rewriting cookie values during replay. 
This allows sessions that use cookies to be replayed against another environment (or the same environment at a later time) correctly.

### Building
To build from source, use the Gradle wrapper.
```
./gradlew build shadow
```

### Usage
```
gor --input-file requests.gor --middleware "java -jar gor-cookie-all.jar" --output-http "http://somewebsite"
```
