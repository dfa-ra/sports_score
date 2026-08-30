FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY release/web/ /usr/share/nginx/html/
EXPOSE 80
