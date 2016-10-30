require_relative "../../test_helper"

class TestProxyRequestRewritingStripsApiKeys < Minitest::Test
  include ApiUmbrellaTests::Setup
  parallelize_me!

  def setup
    setup_server
  end

  def test_strips_api_key_from_header
    assert(self.http_options[:headers]["X-Api-Key"])
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/", self.http_options)
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    refute(data["headers"]["x-api-key"])
  end

  def test_strips_api_key_from_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?api_key=#{self.api_key}", self.http_options.except(:headers))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({}, data["url"]["query"])
  end

  def test_strips_api_key_from_start_of_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?api_key=#{self.api_key}&test=value", self.http_options.except(:headers))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "test" => "value" }, data["url"]["query"])
  end

  def test_strips_api_key_from_end_of_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?test=value&api_key=#{self.api_key}", self.http_options.except(:headers))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "test" => "value" }, data["url"]["query"])
  end

  def test_strips_api_key_from_middle_of_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?test=value&api_key=#{self.api_key}&foo=bar", self.http_options.except(:headers))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "test" => "value", "foo" => "bar" }, data["url"]["query"])
  end

  def test_strips_repeated_api_key_in_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?api_key=#{self.api_key}&api_key=foo&test=value&api_key=bar", self.http_options.except(:headers))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "test" => "value" }, data["url"]["query"])
  end

  def test_strips_invalid_api_key_in_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?api_key=oops_typo_incorrect_api_key&test=value", self.http_options)
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "test" => "value" }, data["url"]["query"])
  end

  def test_strips_empty_api_key_from_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?api_key=&test=value", self.http_options)
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "test" => "value" }, data["url"]["query"])
  end

  def test_strips_boolean_value_api_key_from_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?api_key&test=value&foo", self.http_options)
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "test" => "value", "foo" => true }, data["url"]["query"])
  end

  def test_strips_api_key_from_invalid_encoded_query
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?test=foo%26%20bar&url=%ED%A1%BC&api_key=#{self.api_key}", self.http_options.except(:headers))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal({ "url" => "\xED\xA1\xBC", "test" => "foo& bar" }, data["url"]["query"])
  end

  def test_strips_api_key_from_basic_auth
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/", self.http_options.except(:headers).deep_merge({
      :userpwd => "#{self.api_key}:",
    }))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    refute(data["basic_auth_username"])
    refute(data["headers"]["authorization"])
  end

  def test_retains_basic_auth_if_api_key_passed_by_other_means
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/", self.http_options.deep_merge({
      :userpwd => "foo:",
    }))
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal("foo", data["basic_auth_username"])
    assert(data["headers"]["authorization"])
  end

  def test_preserves_query_string_order
    response = Typhoeus.get("http://127.0.0.1:9080/api/info/?ccc=foo&aaa=bar&api_key=#{self.api_key}&b=test", self.http_options)
    assert_equal(200, response.code, response.body)
    data = MultiJson.load(response.body)
    assert_equal("http://127.0.0.1/info/?ccc=foo&aaa=bar&b=test", data["raw_url"])
  end
end
