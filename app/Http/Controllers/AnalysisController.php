<?php

namespace App\Http\Controllers;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Input;
use Illuminate\Support\Facades\App;
use App\Models\Call_Detail_Records;

class AnalysisController extends Controller{

    public function getIndex() {
   		return view('analysis.analysis');
    }
   	public function test() {
   		$cdr = DB::table('call_detail_records')->where('b_no','2')->pluck('mobile_no');
   		$name = Call_Detail_Records::all();
   		return $name;
   	}
}
?>