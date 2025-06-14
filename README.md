This is a client for the proxy server at https://github.com/bytealizer/OffshoreProxy/

build the docker image
```
docker build --no-cache -t shipproxy:1.0 .
```

run the docker image
```
docker run shipproxy:1.0 --port 8080 --offshoreProxy <offshore-ip> --offshorePort 8081
```

Replace `<offshore-ip>` with the IP address of the local ip of host.
