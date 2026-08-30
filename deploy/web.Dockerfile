FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/templates/default.conf.template
COPY release/web/ /usr/share/nginx/html/
EXPOSE 80
