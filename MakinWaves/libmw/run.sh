PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

docker run --rm -it -v "${PROJECT_DIR}:/work" -w /work --user "$(id -u):$(id -g)" \
       -e USER="${USER}" -e CARGO_HOME=/work/.cargo_home \
       libmw "${@}"
